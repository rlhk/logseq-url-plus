(ns core
  (:require
   [promesa.core :as p]
   [clojure.pprint :refer [pprint]]
   [cuerdas.core :as str]
   [rum.core :as rum]
   ["@logseq/libs"]
   ["link-preview-js" :as link-preview]
   [util :refer [decode-html-content ednize url? else-and-last]]
   [ls] [config] [api] [ui]
   [feat.define :as define]))

(defn modify-block [{:keys [op type mode block child] 
                     :or   {op :default, mode :template}}]
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
                          :tweet-author (-> api-edn (get-in [:includes :users 0 :username]))
                          :but-last    all-but-last}]
          (if (= op :advanced)
            (do
              (println "URL+ Advanced Mode ...")
              (js/logseq.showMainUI))
            (do
              (println "URL+ Formatting block(s) ...")
              (when block (ls/update-block block-uuid, (str/fmt block attrs)))
              (if (= mode :block)
                (ls/insert-batch-block block-uuid, (clj->js (:api-blocks attrs)), (clj->js {:sibling false}))
                (when child (ls/insert-block block-uuid, (str/fmt child attrs))))))))
      (ls/show-msg (str/fmt "Invalid URL: \"%s\"" last-term)))))

(defn advanced-command []
  (println "URL+ Advanced Mode ...")
  (js/logseq.showMainUI))

(defn main []
  (js/logseq.useSettingsSchema (clj->js config/ls-plugin-settings))
  (js/logseq.on "ui:visible:changed" 
                (fn [v]
                  (let [v (ednize v)]
                    (prn "Main UI visibility: " v)
                    (when (true? (:visible v))
                      (println "URL+ Mounting UI ...")
                      (rum/mount (ui/plugin-panel) (.getElementById js/document "app"))))))
  (js/logseq.on "settings:changed" #(prn "settings: " %))
  (doseq [{:keys [desc] :as opts} config/slash-commands]
    (ls/register-slash-command desc, #(modify-block opts)))
  (ls/register-slash-command "URL+ Advanced ..." #(advanced-command))
  ;; (js/logseq.setMainUIInlineStyle (clj->js {}))
  (ls/show-msg "URL+ loaded ..."))

; Logseq handshake
; JS equivalent: `logseq.ready(main).catch(() => console.error)`
(defn init []
  (println "URL+ core.init ...")
  ;; Top level logseq methods have to be called directly
  (-> (p/promise (js/logseq.ready))
      (p/then main)
      (p/catch #(js/console.error))))

(defn reload []
  (println "... core.reload!")
  (rum/mount (ui/plugin-panel) (.getElementById js/document "app"))
  #_(init))