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

;; https://github.com/lambdaisland/uri maybe useful in the future
(defn url? [s]
  (try
    (do (js/URL. s) true)
    (catch js/Object e false)))

(defn content-type
  "Returns a map of :mime-type and :charset from http Content-Type string
   say: 'application/json; charset=utf-8'
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
