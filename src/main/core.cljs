(ns core
  (:require
    [promesa.core :as p]
    [cuerdas.core :as str]
    [clojure.pprint :refer [pprint]]
    [util :refer [decode-html-content ednize url? else-and-last]]
    [ls]
    [api]
    [define]
    ["@logseq/libs"]
    ["link-preview-js" :as link-preview]))

(defn modify-block [{:keys [type mode block child] 
                     :or   {mode :template}}]
  (p/let [current-block (ls/get-current-block)
          block-uuid    (aget current-block "uuid")
          block-content (ls/get-editing-block-content)
          [all-but-last, last-term] (else-and-last block-content)
          [maybe-label, url]  (api/md-link->label-and-url last-term)
          url
          (cond ; we may have other types in the future
            (= type :api/define)  (str "https://api.dictionaryapi.dev/api/v2/entries/en/" last-term)
            (= type :link/define) (str "https://en.wiktionary.org/wiki/" last-term)
            (= type :api/tweet)
            (str "https://api.twitter.com/2/tweets/?tweet.fields=created_at&expansions=author_id&user.fields=created_at&ids="
                 (-> url (str/split #"/") last))
            :else url)
          auth
          (cond
            (= type :api/tweet) {:Authorization (str/fmt "Bearer %s" (aget js/logseq.settings "TwitterAccessToken"))}
            :else nil)]
    (if (url? url)
      (do
        (ls/show-msg (str "Fetching: " url))
        (p/let [meta-res (when (= type :meta) (.getLinkPreview link-preview url))
                meta-edn (ednize meta-res)
                api-res  (-> (p/promise (js/fetch url (when auth (clj->js {:headers auth}))))
                             (p/then   #(.json %))
                             (p/catch  #(js/console.log %)))
                api-edn  (ednize api-res)
                attrs    {:term        last-term
                          :url         url
                          :link-or-url (if maybe-label (str/fmt "[$0]($1)" [maybe-label url]), url)
                          :title       (-> (:title meta-edn) decode-html-content)
                          :description (:description meta-edn)
                          :definition  (-> api-edn define/fmt-definition)
                          :meta-edn    (with-out-str (pprint meta-edn))
                          :meta-json   (js/JSON.stringify meta-res nil 2) ;built-in prettify
                          :meta-attrs  (api/edn->logseq-attrs meta-edn)
                          :api-edn     (-> api-edn pprint with-out-str)
                          :api-json    (js/JSON.stringify api-res nil 2)
                          :api-attrs   (api/edn->logseq-attrs api-edn)
                          :api-blocks  (api/edn->logseq-blocks api-edn)
                          :tweet-text  (-> api-edn (get-in [:data 0 :text]))
                          :tweet-time  (-> api-edn (get-in [:data 0 :created_at]))
                          :tweet-author(-> api-edn (get-in [:includes :users 0 :username]))
                          :but-last    all-but-last}]
          (println "Formatting block(s) ...")
          (when block (ls/update-block block-uuid, (str/fmt block attrs)))
          (if (= mode :block)
            (ls/insert-batch-block block-uuid, (clj->js (:api-blocks attrs)), (clj->js {:sibling false}))
            (when child (ls/insert-block block-uuid, (str/fmt child attrs))))))
      (ls/show-msg (str/fmt "Invalid URL: \"%s\"" last-term)))))

(def commands
  [{:desc "URL+ [title](url)"
    :type :meta ; Supported response types: (or :meta :api :api/define :link/define)
    ;; 2 modes are supported: 
    ;; :template (default, base on string template defined in :block & :child) 
    ;; :block (use :api-blocks attrs) as children blocks. Ignore :child template )
    :mode :template
    :block "%(but-last)s[%(title)s](%(url)s)"}
   {:desc "URL+ [title](url) description"
    :type :meta
    :block "%(but-last)s[%(title)s](%(url)s) %(description)s"}
   {:desc "URL+ Metadata -> Logseq Attributes"
    :type :meta
    :block "%(but-last)s%(link-or-url)s\n%(meta-attrs)s\n"}
   {:desc "URL+ Metadata -> EDN Code"
    :type :meta
    :block "%(but-last)s%(term)s"
    :child "```edn\n%(meta-edn)s```"}
   {:desc "URL+ Metadata -> JSON Code"
    :type :meta
    :block "%(but-last)s%(term)s"
    :child "```json\n%(meta-json)s\n```"} ; :child is optional for mode :template 
   {:desc "URL+ API -> Logseq Attributes"
    :type :api
    :block "%(but-last)s%(link-or-url)s\n%(api-attrs)s\n"}
   {:desc "URL+ API -> Logseq Attribute Blocks"
    :type :api
    :mode :block
    :block "%(but-last)s%(term)s"}
   {:desc "URL+ API -> EDN Code"
    :type :api
    :block "%(but-last)s%(term)s"
    :child "```edn\n%(api-edn)s```"}
   {:desc "URL+ API -> JSON Code"
    :type :api
    :block "%(but-last)s%(term)s"
    :child "```json\n%(api-json)s\n```"}
   {:desc "URL+ Append Definition"
    :type :api/define
    :block "%(but-last)s%(term)s #card"
    :child "%(definition)s"}
   {:desc "URL+ Extract tweet text of twitter.com"
    :type :api/tweet
    :block "%(but-last)s%(term)s #tweet"
    :child "%(tweet-text)s\n%(tweet-author)s (%(tweet-time)s)"}
   {:desc "URL+ Link Wiktionary URL"
    :type :link/define
    :block "%(but-last)s[%(term)s](%(url)s)"}])

(defn main []
  (js/logseq.useSettingsSchema (clj->js api/settings-schema))
  (doseq [{:keys [desc] :as opts} commands]
    (ls/register-slash-command desc, #(modify-block opts)))
  (ls/show-msg "URL+ loaded ..."))

; Handshake with logseq
; JS equivalent: logseq.ready(main).catch(() => console.error)
(defn init []
  (println "... core.init!")
  ;; handshake call `js/logseq.ready` needs to be here 
  ;; won't work when placed in ns `ls` like other js calls
  (-> (p/promise (js/logseq.ready))
      (p/then main)
      (p/catch #(js/console.error))))

(defn reload []
  (println "... core.reload!")
  #_(init))