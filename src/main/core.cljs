(ns core
  (:require
    [promesa.core :as p]
    [cuerdas.core :as str]
    [clojure.pprint :refer [pprint]]
    [plugin :refer [ednize]]
    [word :refer [compact-word-def]]
    ["link-preview-js" :as link-preview]))

(def editor js/logseq.Editor)
(def show-msg js/logseq.UI.showMsg)

(defn rewrite-block [type template]
  (p/let [block         (js/logseq.Editor.getCurrentBlock)
          block-uuid    (aget block "uuid")
          block-content (js/logseq.Editor.getEditingBlockContent)
          [all-but-last, last-term] (plugin/else-and-last block-content)
          url
          (cond ; we may have other types in the future
            (= type :api/word-def) (str "https://api.dictionaryapi.dev/api/v2/entries/en/" last-term)
            (= type :word-linked)  (str "https://en.wiktionary.org/wiki/" last-term)
            :else last-term)]
    (if (plugin/url? url)
      (do
        (show-msg (str "Fetching URL: " url))
        (p/let [url-res  (.getLinkPreview link-preview url)
                meta-edn (ednize url-res)
                api-res  (-> (p/promise (js/fetch url))
                             (p/then  #(.json %))
                             (p/catch #(js/console.log %)))
                api-edn  (ednize api-res)
                template-attrs
                (cond
                  (= type :api/word-def)
                  {:word     last-term
                   :word-def (->> api-res ednize compact-word-def)
                   :but-last all-but-last}

                  (= type :word-linked)
                  {:url      url
                   :word     last-term
                   :but-last all-but-last}

                  :else ; default command type
                  {:url         url
                   :title       (:title meta-edn)
                   :description (:description meta-edn)
                   :meta-edn    (with-out-str (pprint meta-edn))
                   :meta-json   (js/JSON.stringify url-res nil 1)
                   :meta-attrs  (plugin/edn->logseq-attrs meta-edn)
                   :api-edn     (-> api-res ednize pprint with-out-str)
                   :api-json    (js/JSON.stringify api-res nil 2) ; built-in prettify
                   :api-attrs   (plugin/edn->logseq-attrs api-edn)
                   :but-last    all-but-last})]
          (show-msg "Formatting block(s) ...")
          (cond
            (= type :api/word-def)
            (js/logseq.Editor.insertBlock block-uuid, (str/fmt template template-attrs))
            :else
            (js/logseq.Editor.updateBlock block-uuid, (str/fmt template template-attrs)))))
      (show-msg (str/fmt "\"%s\" doesn't seem to be a valid URL!" last-term)))))

(def commands
  [{:desc "URL+ [title](url) description"
    :template "%(but-last)s [%(title)s](%(url)s) %(description)s"
    :type :meta}
   {:desc "URL+ [title](url)"
    :template "%(but-last)s [%(title)s](%(url)s)"
    :type :meta}
   {:desc "URL+ Metadata -> Logseq Attributes"
    :template "%(but-last)s %(url)s\n%(meta-attrs)s\n"
    :type :meta}
   {:desc "URL+ Metadata -> EDN Code"
    :template "%(but-last)s %(url)s\n```edn\n%(meta-edn)s```"
    :type :meta}
   {:desc "URL+ Metadata -> JSON Code"
    :template "%(but-last)s %(url)s\n```json\n%(meta-json)s\n```"
    :type :meta}
   {:desc "URL+ API -> Logseq Attributes"
    :template "%(but-last)s %(url)s\n%(api-attrs)s\n"
    :type :api}
   {:desc "URL+ API -> EDN Code"
    :template "%(but-last)s %(url)s\n```edn\n%(api-edn)s```"
    :type :api}
   {:desc "URL+ API -> JSON Code"
    :template "%(but-last)s %(url)s\n```json\n%(api-json)s\n```"
    :type :api}
   {:desc "URL+ Insert Word Definition"
    :template "%(word-def)s"
    :type :api/word-def}
   {:desc "URL+ Link Wiktionary URL"
    :template "%(but-last)s [%(word)s](%(url)s)"
    :type :word-linked}])

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
