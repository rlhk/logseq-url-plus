(ns util
  (:require
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
