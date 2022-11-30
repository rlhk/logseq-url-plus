(ns core
  (:require
    [promesa.core :as p]
    [cuerdas.core :as str]
    [clojure.pprint :refer [pprint]]
    [plugin :refer [ednize]]
    [dict :refer [fmt-definition]]
    ["link-preview-js" :as link-preview]))

(def show-msg js/logseq.UI.showMsg)

(defn modify-block [{:keys [template type mode] 
                     :or   {mode :child}}]
  (p/let [block         (js/logseq.Editor.getCurrentBlock)
          block-uuid    (aget block "uuid")
          block-content (js/logseq.Editor.getEditingBlockContent)
          [all-but-last, last-term] (plugin/else-and-last block-content)
          [maybe-label, url]  (plugin/md-link->label-and-url last-term)
          url
          (cond ; we may have other types in the future
            (= type :api/define)  (str "https://api.dictionaryapi.dev/api/v2/entries/en/" last-term)
            (= type :link/define) (str "https://en.wiktionary.org/wiki/" last-term)
            :else url)]
    (if (plugin/url? url)
      (do
        (show-msg (str "Fetching URL: " url))
        (p/let [url-res  (.getLinkPreview link-preview url)
                meta-edn (ednize url-res)
                api-res  (-> (p/promise (js/fetch url))
                             (p/then   #(.json %))
                             (p/catch  #(js/console.log %)))
                api-edn  (ednize api-res)
                attrs    {:url         url
                          :term        last-term
                          :link-or-url (if maybe-label (str/fmt "[$0]($1)" [maybe-label url]), url)
                          :definition  (-> api-res ednize fmt-definition)
                          :title       (:title meta-edn)
                          :description (:description meta-edn)
                          :meta-edn    (with-out-str (pprint meta-edn))
                          :meta-json   (js/JSON.stringify url-res nil 2) ;built-in prettify
                          :meta-attrs  (plugin/edn->logseq-attrs meta-edn)
                          :api-edn     (-> api-res ednize pprint with-out-str)
                          :api-json    (js/JSON.stringify api-res nil 2)
                          :api-attrs   (plugin/edn->logseq-attrs api-edn)
                          :api-blocks  (plugin/edn->logseq-blocks api-edn)
                          :but-last    all-but-last}]
          (show-msg "Formatting block(s) ...")
          (cond
            (= mode :inline) (js/logseq.Editor.updateBlock block-uuid, (str/fmt template attrs))
            (= mode :block)  (js/logseq.Editor.insertBatchBlock block-uuid, (clj->js (:api-blocks attrs)), (clj->js {:sibling false}))
            :else (js/logseq.Editor.insertBlock block-uuid, (str/fmt template attrs)))))
      (show-msg (str/fmt "\"%s\" doesn't seem to be a valid URL!" last-term)))))

(def commands
  [{:desc "URL+ [title](url)"
    :type :meta ; response type
    :mode :inline ; logseq block writting mode. Default :child
    :template "%(but-last)s [%(title)s](%(url)s)"}
   {:desc "URL+ [title](url) description"
    :type :meta
    :mode :inline
    :template "%(but-last)s [%(title)s](%(url)s) %(description)s"}
   {:desc "URL+ Metadata -> Logseq Attributes"
    :type :meta
    :mode :inline
    :template "%(but-last)s %(link-or-url)s\n%(meta-attrs)s\n"}
   {:desc "URL+ Metadata -> EDN Code"
    :type :meta
    :template "```edn\n%(meta-edn)s```"}
   {:desc "URL+ Metadata -> JSON Code"
    :type :meta
    :template "```json\n%(meta-json)s\n```"}
   {:desc "URL+ API -> Logseq Attributes"
    :type :api
    :mode :inline
    :template "%(but-last)s %(link-or-url)s\n%(api-attrs)s\n"}
   {:desc "URL+ API -> Logseq Attribute Blocks"
    :type :api
    :mode :block}
   {:desc "URL+ API -> EDN Code"
    :type :api
    :template "```edn\n%(api-edn)s```"}
   {:desc "URL+ API -> JSON Code"
    :type :api
    :template "```json\n%(api-json)s\n```"}
   {:desc "URL+ Append Definition"
    :type :api/define
    :template "%(definition)s"}
   {:desc "URL+ Link Wiktionary URL"
    :type :link/define
    :mode :inline
    :template "%(but-last)s [%(term)s](%(url)s)"}])

(defn main []
  (doseq [{:keys [desc] :as opts} commands]
    (js/logseq.Editor.registerSlashCommand desc, #(modify-block opts)))
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
