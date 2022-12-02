(ns core
  (:require
    [promesa.core :as p]
    [cuerdas.core :as str]
    [clojure.pprint :refer [pprint]]
    [plugin :refer [ednize]]
    [dict :refer [fmt-definition]]
    ["link-preview-js" :as link-preview]))

(def show-msg js/logseq.UI.showMsg)

(defn modify-block [{:keys [type mode block child] 
                     :or   {mode :template}}]
  (p/let [current-block (js/logseq.Editor.getCurrentBlock)
          block-uuid    (aget current-block "uuid")
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
        (p/let [meta-res (when (= type :meta) (.getLinkPreview link-preview url))
                meta-edn (ednize meta-res)
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
                          :meta-json   (js/JSON.stringify meta-res nil 2) ;built-in prettify
                          :meta-attrs  (plugin/edn->logseq-attrs meta-edn)
                          :api-edn     (-> api-res ednize pprint with-out-str)
                          :api-json    (js/JSON.stringify api-res nil 2)
                          :api-attrs   (plugin/edn->logseq-attrs api-edn)
                          :api-blocks  (plugin/edn->logseq-blocks api-edn)
                          :but-last    all-but-last}]
          (show-msg "Formatting block(s) ...")
          (when block (js/logseq.Editor.updateBlock block-uuid, (str/fmt block attrs)))
          (if (= mode :block)
            (js/logseq.Editor.insertBatchBlock block-uuid, (clj->js (:api-blocks attrs)), (clj->js {:sibling false}))
            (when child (js/logseq.Editor.insertBlock block-uuid, (str/fmt child attrs))))))
      (show-msg (str/fmt "Invalid URL: \"%s\"" last-term)))))

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
   {:desc "URL+ Link Wiktionary URL"
    :type :link/define
    :block "%(but-last)s[%(term)s](%(url)s)"}])

(defn main []
  (doseq [{:keys [desc] :as opts} commands]
    (js/logseq.Editor.registerSlashCommand desc, #(modify-block opts)))
  (show-msg "URL+ loaded ..."))

; Standard logseq startup
; JS equivalent: logseq.ready(main).catch(() => console.error)
(defn init []
  (println "... core.init!")
  (-> (p/promise (js/logseq.ready))
      (p/then main)
      (p/catch #(js/console.error))))

(defn reload []
  (println "... core.reload!")
  (init))
