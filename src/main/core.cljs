(ns core
  (:require
   [clojure.pprint :refer [pprint]]
   [cuerdas.core :as str]
   [promesa.core :as p]
   [rum.core :as rum]
   ["@logseq/libs"]
   ["link-preview-js" :as link-preview]
   [util :as u :refer [devlog decode-html-content ednize http? else-and-last remove-url-trackers]]
   [ls] [config :refer [plugin-state]] [api] [ui]
   [feat.define :as define]))

(def ^:private dictionary-api-base
  "https://api.dictionaryapi.dev/api/v2/entries/en/")

(def ^:private tweet-api-base
  (str "https://api.twitter.com/2/tweets/?tweet.fields=created_at"
       "&expansions=author_id&user.fields=created_at&ids="))

(def ^:private api-types
  "Command types whose payload comes from a JSON API rather than page metadata.
  Everything else must not trigger an API fetch - see `handle-slash-cmd`."
  #{:api :api/define :api/tweet})

(defn tokenize-setting-str [setting-key]
  (let [setting-str (some-> js/logseq.settings (aget setting-key) js->clj)]
    (if (str/blank? setting-str)
      []
      (u/tokenize-str setting-str))))

(defn- resolve-url
  "Derive the URL to fetch for a command type from the block's last token."
  [type last-token token-url]
  (case type
    :api/define  (str dictionary-api-base last-token)
    :link/define (str "https://en.wiktionary.org/wiki/" last-token)
    :api/tweet   (str tweet-api-base (-> token-url (str/split #"/") last))
    token-url))

(defn- auth-headers
  "Authorization headers for command types that need them, else nil."
  [type]
  (when (= type :api/tweet)
    {:Authorization
     (str/fmt "Bearer %s" (some-> js/logseq.settings (aget "TwitterAccessToken")))}))

(defn- fetch-link-preview
  "Fetch page metadata, resolving to nil instead of rejecting on failure.

  Redirects are followed with link-preview-js's default behaviour. The
  previous version passed `:followRedirects \"manual\"` with a handler that
  returned false for any cross-host hop, which makes the library throw - so
  every URL shortener (youtu.be, t.co, bit.ly) failed outright."
  [url]
  (-> (.getLinkPreview link-preview url)
      (p/catch (fn [err]
                 (devlog "getLinkPreview failed:" err)
                 nil))))

(defn handle-slash-cmd [{:keys [type mode block child]
                         :or   {mode :template}}]
  (p/let [current-block (ls/get-current-block)
          block-content (ls/get-editing-block-content)]
    (let [block-uuid (some-> current-block (aget "uuid"))
          [all-but-last, last-token] (when (string? block-content)
                                       (else-and-last block-content))]
      (cond
        ;; `getEditingBlockContent` resolves to null when nothing is being
        ;; edited; the old code fed that straight into a regex and threw.
        (not block-uuid)
        (ls/show-msg "URL+: no block is being edited.")

        (str/blank? last-token)
        (ls/show-msg "URL+: the current block has no token to work with.")

        :else
        (p/let [[maybe-label, token-url] (api/md-link->label-and-url last-token)
                url (-> (resolve-url type last-token token-url)
                        u/canonicalize-url
                        remove-url-trackers)]
          (if-not (http? url)
            (ls/show-msg (str/fmt "URL+: invalid URL \"%s\"" last-token))
            (p/let [_ (ls/show-msg (str "Processing URL: " url))
                    _ (devlog "Resolved URL:" url)
                    ;; Only :meta commands read page metadata ...
                    meta-res (when (= type :meta) (fetch-link-preview url))
                    ;; ... and only :api* commands hit a JSON API. Previously
                    ;; both ran for every command, so a plain title lookup
                    ;; fetched the page twice.
                    api-json (when (contains? api-types type)
                               (ls/fetch-api url (auth-headers type)))
                    api-edn  (when api-json (ednize api-json))
                    api-err  (ls/api-error api-edn)]
              (cond
                (and (= type :meta) (nil? meta-res))
                (ls/show-msg (str "URL+: could not read metadata from " url))

                api-err
                (ls/show-msg (str "URL+: " api-err
                                  (when-let [m (:message api-edn)] (str " - " m))))

                :else
                (p/let [meta-edn (when meta-res
                                   (u/exclude-include-ks
                                    (ednize meta-res)
                                    (map keyword (tokenize-setting-str "UrlPlusExcludeAttrs"))
                                    (map keyword (tokenize-setting-str "UrlPlusIncludeAttrs"))))
                        clean    (fn [v] (or (some-> v decode-html-content u/md-inline-escape) ""))
                        attrs    {:token       last-token
                                  :url         url
                                  :link-or-url (if maybe-label
                                                 (str/fmt "[$0]($1)" [maybe-label url])
                                                 url)
                                  ;; Remote text lands in the user's graph, so
                                  ;; escape it before it can inject markdown or
                                  ;; Logseq property/macro syntax.
                                  :title       (clean (:title meta-edn))
                                  :description (clean (:description meta-edn))
                                  :definition  (if api-edn (define/fmt-definition api-edn) "")
                                  :meta-edn    (if meta-edn (with-out-str (pprint meta-edn)) "")
                                  :meta-json   (if meta-res (js/JSON.stringify meta-res nil 2) "")
                                  :meta-attrs  (if meta-edn (api/edn->logseq-attrs meta-edn) "")
                                  :api-edn     (if api-edn (with-out-str (pprint api-edn)) "")
                                  :api-json    (if api-json (js/JSON.stringify api-json nil 2) "")
                                  :api-attrs   (if api-edn (api/edn->logseq-attrs api-edn) "")
                                  :api-blocks  (if api-edn (api/edn->logseq-blocks api-edn) [])
                                  :tweet-text   (-> api-edn :data first :text)
                                  :tweet-time   (-> api-edn :data first :created_at)
                                  :tweet-author (-> api-edn :includes :users first :username)
                                  :but-last    all-but-last}]
                  (devlog "Formatting block(s) ...")
                  (p/let [_ (when block (ls/update-block block-uuid (str/fmt block attrs)))]
                    (if (= mode :block)
                      (ls/insert-batch-block block-uuid
                                             (clj->js (:api-blocks attrs))
                                             (clj->js {:sibling false}))
                      (when child
                        (ls/insert-block block-uuid (str/fmt child attrs))))))))))))))

(defn show-inspector-ui []
  (devlog "Inspector mode ...")
  (js/logseq.showMainUI)
  (p/let [current-block (ls/get-current-block)
          block-content (ls/get-editing-block-content)]
    (let [block-uuid (some-> current-block (aget "uuid"))
          [block-before-token, last-token] (when (string? block-content)
                                             (else-and-last block-content))]
      (if-not block-uuid
        (ls/show-msg "URL+: no block is being edited.")
        (p/let [[maybe-label, token-url] (if (str/blank? last-token)
                                           [nil nil]
                                           (api/md-link->label-and-url last-token))
                url (when token-url
                      (-> token-url u/canonicalize-url remove-url-trackers))]
          (swap! plugin-state merge
                 {:token last-token
                  :token-label maybe-label
                  :block-content block-content
                  :block-content-before-token block-before-token
                  :url (when (http? url) url)
                  :block {:uuid block-uuid}})
          (if (http? url)
            (do
              (swap! plugin-state assoc-in [:option :semantics] :website)
              (swap! plugin-state assoc-in [:meta-edn :msg] "Loading")
              (p/let [meta-res (fetch-link-preview url)
                      meta-edn (when meta-res (ednize meta-res))
                      ;; The old code tested `(= type :api/tweet)` here, but
                      ;; `type` was never bound - it resolved to clojure.core/type,
                      ;; so the branch could never fire. The inspector has no
                      ;; command type, so no auth header applies.
                      api-json (ls/fetch-api url nil)
                      api-edn  (ednize api-json)
                      api-err  (ls/api-error api-edn)
                      api-record-count (if api-err 0 (count api-edn))]
                (swap! plugin-state merge
                       {:meta-edn (or meta-edn {:msg (str "Could not read metadata from " url)})
                        :api-edn (when-not api-err api-edn)
                        :api-record-count api-record-count})
                (when (pos? api-record-count)
                  (swap! plugin-state assoc-in [:option :semantics] :api))))
            (swap! plugin-state assoc-in [:option :semantics] :word)))))))

(defn cmd-enabled? [m]
  ;; `logseq.settings` is null until the schema has hydrated; `aget` on null
  ;; throws, and this runs during `main`.
  (boolean (some-> js/logseq.settings (aget (:setting-key m)))))

(defonce ^:private slash-commands-registered?
  ;; `defonce` so a shadow-cljs hot reload does not reset the guard.
  (atom false))

(defn- register-slash-commands! []
  (if @slash-commands-registered?
    ;; `reload` re-enters `main`, and Logseq has no unregister API, so without
    ;; this guard every hot reload added another copy of every command.
    ;; Long-standing TODO from commit 11461a1.
    (devlog "Slash commands already registered; skipping.")
    (do
      (when (cmd-enabled? {:setting-key "UrlPlusInspector"})
        (ls/register-slash-command "URL+ Inspector ..." #(show-inspector-ui)))
      (doseq [{:keys [desc] :as opts} (filter cmd-enabled? config/slash-commands)]
        (devlog "Registering:" desc)
        (ls/register-slash-command desc, #(handle-slash-cmd opts)))
      (reset! slash-commands-registered? true))))

(defn main []
  (js/logseq.useSettingsSchema (clj->js config/ls-plugin-settings))
  (js/logseq.on 
   "ui:visible:changed"
   (fn [v]
     (let [v (ednize v)]
       (devlog "Main UI visibility: " v)
       (if (:visible v)
         (do
           (devlog "Mounting UI ...")
           (rum/mount (ui/plugin-panel) (.getElementById js/document "app")))
         (do
           (devlog "Unmounting UI ...")
           (swap! plugin-state select-keys config/persistent-state-keys))))))
  (js/logseq.on "settings:changed" #(devlog "settings: " %))
  (ls/register-js-events)
  (register-slash-commands!)
  (ls/show-msg "URL+ loaded ..."))

; Logseq handshake
; JS equivalent: `logseq.ready(main).catch(() => console.error)`
(defn init []
  (devlog "core.init ...")
  ;; Top level logseq methods have to be called directly
  (-> (p/promise (js/logseq.ready))
      (p/then main)
      (p/catch #(js/console.error "URL+ init failed:" %))))

(defn reload []
  (devlog "... core.reload!")
  (rum/mount (ui/plugin-panel) (.getElementById js/document "app"))
  (init))

(comment
  (ls/reload-plugin "logseq-url-plus"))