; CAUTION: ONLY allows pure fns to facilitate Node.js based TDD setup
(ns plugin
  (:require
    [cuerdas.core :as str]))

; TODO
(def settings-schema
  [{}])

(defn ednize [data]
  (js->clj data :keywordize-keys true))

(defn url? [s]
  (try
    (do (js/URL. s) true)
    (catch js/Object e false)))

; https://stackoverflow.com/questions/15020669/clojure-multiline-regular-expression
(defn else-and-last [s]
  (->> (str/rtrim s)
       (re-find #"(?is)(.*?)\s*(\S+?)$")
       rest))

(defn edn->logseq-attrs
  "Convert EDN data to Logseq attributes string"
  [data]
  (let [maybe-map (if (sequential? data) (first data) data)
        m (when (map? maybe-map) maybe-map)
        warning (if (map? m)
                  (if (sequential? data) "Data is a collection. Taking the first map-like record.")
                  "Can't determine a map-like record.")]
    (str
      (when warning (str "#+BEGIN_WARNING\n" warning "\n#+END_WARNING\n"))
      (str/join "\n" (for [[k v] m] (str (name k) ":: " v))))))

(defn condensed-def [data]
  (-> (get-in data [0 :meanings 0]) 
      ((juxt :partOfSpeech #(get-in % [:definitions 0 :definition])))))

(defn unabridged-word [data]
  (let [sound     (get-in data [:phonetic])
        meanings  (get-in data [:meanings])]
        (-> (map data))        
        
        ))
(defn pos-g [group]
    (mapv str (iterate inc 1) (map #(get % :definition)group)))