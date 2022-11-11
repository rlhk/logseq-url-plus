(ns core
  (:require
    [promesa.core :as p]
    [cuerdas.core :as str]
    [clojure.pprint :refer [pprint]]
    [plugin :refer [ednize]]
    ["link-preview-js" :as link-preview]))

(def editor js/logseq.Editor)
(def show-msg js/logseq.UI.showMsg)

(defn rewrite-url [url template not-last block-id]
  (do
    (show-msg (str "Fetching URL: " url))
    (p/let [meta-res (.getLinkPreview link-preview url)
            meta-edn (ednize meta-res)
            api-res  (-> (p/promise (js/fetch url))
                          (p/then  #(.json %))
                          (p/catch #(js/console.log %)))
            api-edn  (ednize api-res)]
      (js/logseq.Editor.updateBlock block-id
        (str/fmt template
          {:url         url
            :title       (:title meta-edn)
            :description (:description meta-edn)
            :meta-edn    (with-out-str (pprint meta-edn))
            :meta-json   (js/JSON.stringify meta-res nil 1)
            :meta-attrs  (plugin/edn->logseq-attrs meta-edn)
            :api-edn     (-> api-res ednize pprint with-out-str)
            :api-json    (js/JSON.stringify api-res nil 2) ; built-in prettify
            :api-attrs   (plugin/edn->logseq-attrs api-edn)
            :else        not-last})))))

(defn find-definitions [word template not-last block-id]
  (show-msg (str "Fetching URL: " (str "https://api.dictionaryapi.dev/api/v2/entries/en/" word)))
  (p/let [url      (str "https://api.dictionaryapi.dev/api/v2/entries/en/" word)
          meta-res (.getLinkPreview link-preview url)
          meta-edn (ednize meta-res)
          api-res  (-> (p/promise (js/fetch url))
                        (p/then  #(.json %))
                        (p/catch #(js/console.log %)))]
    (js/logseq.Editor.updateBlock block-id
      (str/fmt template
        { :word         word
          :api-def     (->> api-res ednize plugin/condensed-def (str/join " "))
          :else        not-last}))))

(defn rewrite-block [templates]
  (show-msg "Rewriting block ...")
  (p/let [block (js/logseq.Editor.getCurrentBlock)
          block-uuid (aget block "uuid")
          block-content (js/logseq.Editor.getEditingBlockContent)
          [all-but-last maybe-url] (plugin/else-and-last block-content)]
    (cond 
      (plugin/url? maybe-url) (rewrite-url maybe-url templates all-but-last block-uuid)
      :else (find-definitions maybe-url templates all-but-last block-uuid))))

(def command-set
  {
   "URL+ [title](url) description"
   "%(else)s [%(title)s](%(url)s) %(description)s"

   "URL+ [title](url)"
   "%(else)s [%(title)s](%(url)s)"

   "URL+ Metadata -> Logseq Attributes"
   "%(else)s %(url)s\n%(meta-attrs)s\n"

   "URL+ Metadata -> EDN Code"
   "%(else)s %(url)s\n```edn\n%(meta-edn)s```"

   "URL+ Metadata -> JSON Code"
   "%(else)s %(url)s\n```json\n%(meta-json)s\n```"

   "URL+ API -> Logseq Attributes"
   "%(else)s %(url)s\n%(api-attrs)s\n"

   "URL+ API -> EDN Code"
   "%(else)s %(url)s\n```edn\n%(api-edn)s```"

   "URL+ API -> JSON Code"
   "%(else)s %(url)s\n```json\n%(api-json)s\n```"
   
   "URL+ Compact Definitions"
   "%(else)s %(word)s %(api-def)s"})

(defn main []
  (doseq [[cmd template] command-set]
    (js/logseq.Editor.registerSlashCommand cmd, #(rewrite-block template)))
  (show-msg "URL+ loaded ..."))

; Standard logseq startup
; JS equivalent: logseq.ready(main).catch(() => console.error)
(defn init! []
  (println "... core.init!")
  (-> (p/promise (js/logseq.ready))
      (p/then main)
      (p/catch #(js/console.error))))

(defn reload! []
  (println "... core.reload!")
  (init!))
