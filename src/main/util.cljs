(ns util
  "Utility helpers for data transformation, DOM etc..."
  (:require
   [clojure.walk :refer [keywordize-keys]]
   [medley.core :refer [filter-vals]]
   [cuerdas.core :as str]
   [goog.html.textExtractor :as gtext]))

;; https://github.com/google/closure-library/blob/master/closure/goog/html/textextractor.js#L13
(def decode-html-content gtext/extractTextContent)

(defn devlog [& msgs]
  (when goog.DEBUG
    (apply js/console.log (into ["URL+"] msgs))))

(defn target-value [e]
  (.. e -target -value))

(defn target-checked [e]
  (.. e -target -checked))

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
  "Parse a string and return a map of :label & :link."
  [s]
  (some->> (str/trim s)
           (re-find #"\[(.*?)\]\((.*?)\)")
           rest
           (#(-> {:label (first %) :link (second %)}))))

(defn md-link->str [{:keys [label link]}]
  (str/format "[%s](%s)" label link))

(defn md-link? [s]
  (some? (:link (str->md-link s))))

(comment
  (str->md-link "[I'm label](I'm link)")
  (md-link->str {:label "I'm label" :link "Just a link"})
  )

(defn content-type
  "Return a map of :mime-type and :charset etc... 
   from http Content-Type string say: 'application/json; charset=utf-8'
   TODO: Better use proper http client lib"
  [s]
  (some->> (str/trim s)
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

(defn records? [data]
  (and (sequential? data), (map? (first data))))
