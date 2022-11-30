; CAUTION: ONLY allows pure fns to facilitate Node.js based TDD setup
(ns plugin
  (:require
   [medley.core :refer [filter-vals]]
   [cuerdas.core :as str]))

; TODO
(def settings-schema
  [{}])

(defn ednize [data]
  (js->clj data :keywordize-keys true))

;; https://github.com/lambdaisland/uri maybe useful in the future
(defn url? [s]
  (try
    (do (js/URL. s) true)
    (catch js/Object e false)))

; https://stackoverflow.com/questions/15020669/clojure-multiline-regular-expression
(defn else-and-last [s]
  (->> (str/rtrim s)
       (re-find #"(?is)(.*?)\s*(\S+?)$")
       rest))

(defn md-link->array
  "Convert logseq link to label + url array, or retain url"
  [input]
  (let [output (re-find #"\[(.*?)\]\((.*?)\)" input)]
    (if output
      (rest output)
      [nil input])))

(defn nested? [data]
  (cond
    (not (or (map? data) (sequential? data))) false
    (not (seq data)) false
    (or (map? data) (sequential? data)) true
    :else false))

(defn attrs-and-children
  "Split flat values and nested values of a map"
  [data]
  ;; (prn "attrs-and-children ..." data)
  [(filter-vals #(not (nested? %)) data)
   (filter-vals #(nested? %) data)])

(defn edn->logseq-attrs
  "Convert EDN data to Logseq attributes string. Assume input is map."
  [data]
  (let [maybe-map (if (sequential? data) (first data) data)
        m (when (map? maybe-map) maybe-map)
        ;; warning (if (map? m)
                  ;; (when (sequential? data) "Data is a collection. Taking the first map-like record.")
                  ;; "Can't determine a map-like record.")
        ]
    (str
     #_(when warning (str "#+BEGIN_WARNING\n" warning "\n#+END_WARNING\n"))
     (str/join "\n" (for [[k v] m] (str (name k) ":: " v))))))

(defn edn->logseq-blocks
  "Convert EDN data to Logseq attribute blocks"
  [data]
  (if (sequential? data)
    (map #(identity {:content (edn->logseq-attrs %)}) data)
    {:content (edn->logseq-attrs data)}))

