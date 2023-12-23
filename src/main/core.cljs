(ns core
  (:require
   [clojure.pprint :refer [pprint]]
   [cuerdas.core :as str]
   [promesa.core :as p]
   [rum.core :as rum]
   ["@logseq/libs"]
   ["link-preview-js" :as link-preview]
   [util :as u :refer [devlog decode-html-content ednize http? else-and-last]]
   [ls] [config :refer [plugin-state]] [api] [ui]
   [feat.define :as define]))

(defn tokenize-setting-str [setting-key]
  (let [setting-str (js->clj (aget js/logseq.settings setting-key))]
    (if (str/blank? setting-str)
      []
      (u/tokenize-str setting-str))))

(defn handle-slash-cmd [{:keys [type mode block child]
                         :or   {mode :template}}]
  (p/let [current-block (ls/get-current-block)
          block-uuid    (aget current-block "uuid")
          block-content (ls/get-editing-block-content)
          [all-but-last, last-token] (else-and-last block-content)
          [maybe-label, url]  (api/md-link->label-and-url last-token)
          url
          (cond ; we may have other types in the future
            (= type :api/define)  (str "https://api.dictionaryapi.dev/api/v2/entries/en/" last-token)
            (= type :link/define) (str "https://en.wiktionary.org/wiki/" last-token)
            (= type :api/tweet)
            (str "https://api.twitter.com/2/tweets/?tweet.fields=created_at&expansions=author_id&user.fields=created_at&ids="
                 (-> url (str/split #"/") last))
            :else url)
          auth
          (cond
            (= type :api/tweet) {:Authorization (str/fmt "Bearer %s" (aget js/logseq.settings "TwitterAccessToken"))}
            :else nil)]
    (if (http? url)
      (do
        (ls/show-msg (str "Fetching: " url))
        (p/let [meta-res (when (= type :meta) (.getLinkPreview link-preview url))
                meta-edn (u/exclude-include-ks
                          (ednize meta-res)
                          (map keyword (tokenize-setting-str "UrlPlusExcludeAttrs"))
                          (map keyword (tokenize-setting-str "UrlPlusIncludeAttrs")))
                api-json (-> (ls/fetch-api url auth)
                             (p/then   #(-> %))
                             (p/catch  #(js/console.log %)))
                api-edn  (ednize api-json)
                attrs    {:token       last-token
                          :url         url
                          :link-or-url (if maybe-label (str/fmt "[$0]($1)" [maybe-label url]), url)
                          :title       (-> (:title meta-edn) decode-html-content)
                          :description (:description meta-edn)
                          :definition  (-> api-edn define/fmt-definition)
                          :meta-edn    (with-out-str (pprint meta-edn))
                          :meta-json   (js/JSON.stringify meta-res nil 2) ;JS built-in prettify
                          :meta-attrs  (api/edn->logseq-attrs meta-edn)
                          :api-edn     (-> api-edn pprint with-out-str)
                          :api-json    (js/JSON.stringify api-json nil 2)
                          :api-attrs   (api/edn->logseq-attrs api-edn)
                          :api-blocks  (api/edn->logseq-blocks api-edn)
                          :tweet-text   (-> api-edn :data first :text)
                          :tweet-time   (-> api-edn :data first :created_at)
                          :tweet-author (-> api-edn :includes :users first :username)
                          :but-last    all-but-last}]
          (do
            (devlog "Formatting block(s) ...")
            (when block (ls/update-block block-uuid, (str/fmt block attrs)))
            (if (= mode :block)
              (ls/insert-batch-block block-uuid, (clj->js (:api-blocks attrs)), (clj->js {:sibling false}))
              (when child (ls/insert-block block-uuid, (str/fmt child attrs)))))))
      (ls/show-msg (str/fmt "Invalid URL: \"%s\"" last-token)))))

(defn show-inspector-ui []
  (devlog "Inspector mode ...")
  (js/logseq.showMainUI)
  (p/let [current-block (ls/get-current-block)
          block-uuid    (aget current-block "uuid")
          block-content (ls/get-editing-block-content)
          [block-before-token, last-token] (else-and-last block-content)
          [maybe-label, url]  (api/md-link->label-and-url last-token)]
    (swap! plugin-state merge {:token last-token
                               :token-label maybe-label
                               :block-content block-content
                               :block-content-before-token block-before-token
                               :url (when (http? url) url)
                               :block {:uuid block-uuid}})
    (if (http? url)
      (do
        (swap! plugin-state assoc-in [:option :semantics] :website)
        (swap! plugin-state assoc-in [:meta-edn :msg] "Loading")
        (p/let [meta-res (.getLinkPreview link-preview url)
                meta-edn (ednize meta-res)
                auth (cond
                       (= type :api/tweet) {:Authorization (str/fmt "Bearer %s" (aget js/logseq.settings "TwitterAccessToken"))}
                       :else nil)
                api-json (-> (ls/fetch-api url auth)
                             (p/then   #(-> %))
                             (p/catch  #(js/console.log %)))
                api-edn  (ednize api-json)
                api-record-count (count api-edn)]
          (swap! plugin-state
                 merge {;:meta-json (js/JSON.stringify meta-res nil 2)
                        :meta-edn meta-edn
                        ;:api-json (js/JSON.stringify api-json nil 2)
                        :api-edn api-edn
                        :api-record-count api-record-count})
          (when (pos? api-record-count)
            (swap! plugin-state assoc-in [:option :semantics] :api))))
      (swap! plugin-state assoc-in [:option :semantics] :word))))

(defn cmd-enabled? [m] (aget js/logseq.settings (:setting-key m)))

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
  (when (cmd-enabled? {:setting-key "UrlPlusInspector"})
    (ls/register-slash-command "URL+ Inspector ..." #(show-inspector-ui)))
  (doseq [{:keys [desc] :as opts} (filter cmd-enabled? config/slash-commands)]
    (devlog "Registering:" desc)
    (ls/register-slash-command desc, #(handle-slash-cmd opts)))
  (ls/show-msg "URL+ loaded ..."))

; Logseq handshake
; JS equivalent: `logseq.ready(main).catch(() => console.error)`
(defn init []
  (devlog "core.init ...")
  ;; Top level logseq methods have to be called directly
  (-> (p/promise (js/logseq.ready))
      (p/then main)
      (p/catch #(js/console.error))))

(defn reload []
  (devlog "... core.reload!")
  (rum/mount (ui/plugin-panel) (.getElementById js/document "app"))
  (init))

(comment
  (ls/reload-plugin "logseq-url-plus"))