(ns util
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [medley.core :refer [filter-vals]]
   [cuerdas.core :as str]
   [goog.html.textExtractor :as gtext]))

;; https://github.com/google/closure-library/blob/master/closure/goog/html/textextractor.js#L13
(def decode-html-content gtext/extractTextContent)

(defn ednize [data]
  (js->clj data :keywordize-keys true))

;; https://github.com/lambdaisland/uri maybe useful
(defn url? [s]
  (try
    (do (js/URL. s) true)
    (catch js/Object e false)))

(defn http? [s]
  (and (str/starts-with? s "http")
       (url? s)))

(defn str->md-link
  "Return a map of :label & :link."
  [s]
  (->> (str/trim s)
       (re-find #"\[(.*?)\]\((.*?)\)")
       rest
       (#(-> {:label (first %) :link (second %)}))))

(defn md-link->str [{:keys [label link]}]
  (str/format "[%s](%s)" label link))

(comment
  (str->md-link "[I'm label](I'm link)")
  (md-link->str {:label "I'm label" :link "Just a link"})
  )

(defn content-type
  "Return a map of :mime-type and :charset etc... 
   from http Content-Type string say: 'application/json; charset=utf-8'
   TODO: Better use proper http client lib"
  [s]
  (->> (str/trim s)
       (re-find #"(.*?);\s*(.*?)$")
       rest
       (#(into {:mime-type (first %)}
               (vector (-> % second (str/split #"=")))))
       keywordize-keys))

(defn json-response? 
  [content-type-str]
  (= "application/json" (:mime-type (content-type content-type-str))))

(comment
  (content-type "application/json; charset=utf-8")
  (json-response? "application/json; charset=utf-8"))

(defn nested? [data]
  (cond
    (not (or (map? data) (sequential? data))) false
    (not (seq data)) false
    (or (map? data) (sequential? data)) true
    :else false))

(defn attrs-and-children
  "Split flat values and nested values of a map"
  [data]
  [(filter-vals #(not (nested? %)) data)
   (filter-vals #(nested? %) data)])

; https://stackoverflow.com/questions/15020669/clojure-multiline-regular-expression
(defn else-and-last [s]
  (->> (str/rtrim s)
       (re-find #"(?is)(.*?\s*)(\[.*?\]\(.*?\)|\S+?)$")
       rest))

(defn to-fixed [number places]
  (.toFixed number places))

(defn reload-plugin [plugin-id]
  ;; In JS console: LSPluginCore.reload("logseq-url-plus")
  ;; cljs REPL runtime lives in an iframe. 
  ;; Thus `top ` required to call LSPluginCore in parent.
  (js-invoke js/top.LSPluginCore "reload" plugin-id))
