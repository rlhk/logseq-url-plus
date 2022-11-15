(ns core
  (:require
    [promesa.core :as p]
    [cuerdas.core :as str]
    [clojure.pprint :refer [pprint]]
    [plugin :refer [ednize]]
    [word :refer [compact-def]]
    ["link-preview-js" :as link-preview]))

(def editor js/logseq.Editor)
(def show-msg js/logseq.UI.showMsg)

(defn rewrite-block [type template]
  (show-msg "Rewriting block ...")
  (p/let [block (js/logseq.Editor.getCurrentBlock)
          block-uuid (aget block "uuid")
          block-content (js/logseq.Editor.getEditingBlockContent)
          [all-but-last maybe-url] (plugin/else-and-last block-content)]
    (cond
      (plugin/url? maybe-url)
      (do
        (show-msg (str "Fetching URL: " maybe-url))
        (p/let [meta-res (.getLinkPreview link-preview maybe-url)
                meta-edn (ednize meta-res)
                api-res  (-> (p/promise (js/fetch maybe-url))
                             (p/then  #(.json %))
                             (p/catch #(js/console.log %)))
                api-edn  (ednize api-res)]
          (js/logseq.Editor.updateBlock block-uuid
            (str/fmt template
              {:url         maybe-url
               :title       (:title meta-edn)
               :description (:description meta-edn)
               :meta-edn    (with-out-str (pprint meta-edn))
               :meta-json   (js/JSON.stringify meta-res nil 1)
               :meta-attrs  (plugin/edn->logseq-attrs meta-edn)
               :api-edn     (-> api-res ednize pprint with-out-str)
               :api-json    (js/JSON.stringify api-res nil 2) ; built-in prettify
               :api-attrs   (plugin/edn->logseq-attrs api-edn)
               :else        all-but-last}))))
      (= type :api/word)
      (do
        (show-msg (str "Fetching word definition: " maybe-url))
        (p/let [dict-url (str "https://api.dictionaryapi.dev/api/v2/entries/en/" maybe-url)
                api-res  (-> (p/promise (js/fetch dict-url))
                             (p/then  #(.json %))
                             (p/catch #(js/console.log %)))]
          (js/logseq.Editor.updateBlock block-uuid
            (str/fmt template
              {:word maybe-url
               :word-def (->> api-res ednize compact-def)
               :else all-but-last}))))
      :else
      (show-msg (str/fmt "\"%s\" doesn't seem to be a valid URL!" maybe-url)))))

(def commands
  [{:desc "URL+ [title](url) description"
    :template "%(else)s [%(title)s](%(url)s) %(description)s"
    :type :meta}
   {:desc "URL+ [title](url)"
    :template "%(else)s [%(title)s](%(url)s)"
    :type :meta}
   {:desc "URL+ Metadata -> Logseq Attributes"
    :template "%(else)s %(url)s\n%(meta-attrs)s\n"
    :type :meta}
   {:desc "URL+ Metadata -> EDN Code"
    :template "%(else)s %(url)s\n```edn\n%(meta-edn)s```"
    :type :meta}
   {:desc "URL+ Metadata -> JSON Code"
    :template "%(else)s %(url)s\n```json\n%(meta-json)s\n```"
    :type :meta}
   {:desc "URL+ API -> Logseq Attributes"
    :template "%(else)s %(url)s\n%(api-attrs)s\n"
    :type :api}
   {:desc "URL+ API -> EDN Code"
    :template "%(else)s %(url)s\n```edn\n%(api-edn)s```"
    :type :api}
   {:desc "URL+ API -> JSON Code"
    :template "%(else)s %(url)s\n```json\n%(api-json)s\n```"
    :type :api}
   {:desc "URL+ Compact Definition"
    :template "%(else)s <h2>%(word)s</h2> %(word-def)s"
    :type :api/word}])

(defn main []
  (doseq [{:keys [desc template type]} commands]
    (js/logseq.Editor.registerSlashCommand desc, #(rewrite-block type template)))
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
