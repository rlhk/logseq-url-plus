(ns feat.define
  "Feature of appending dictionary definitions."
  (:require
   [cuerdas.core :as str]))

(defn fmt-phonetic [phonetic]
  (cond 
    (str/blank? (:audio phonetic))
    (str/fmt "%(text)s" phonetic)

    (not (:text phonetic))
    (str/fmt "[audio](%(audio)s)" phonetic)

    :else
    (str/fmt "[%(text)s](%(audio)s)" phonetic)))

(defn fmt-phonetics [phonetics]
  (->> phonetics
       ; (remove #(str/blank? (:audio %)))
       (map fmt-phonetic)
       (map-indexed #(str/fmt "**%s.** %s" (inc %1) %2))
       (str/join " ")))

(defn fmt-part-of-speech [pos]
  (let [pos-name (->> pos :partOfSpeech)
        meanings (->> pos :definitions (map :definition))]
    (str
      (str/fmt "【%s】 " pos-name)
      (str/join " "
        (->> meanings
             (map-indexed #(str/fmt "**%s.** %s", (inc %1), %2)))))))

(defn fmt-definition [api-edn]
  (let [word (first api-edn)
        phonetics
        (->> word :phonetics
             (fmt-phonetics))
        pos
        (->> word :meanings
             (map fmt-part-of-speech)
             (str/join "; "))]
    (str/join "; " (remove #(str/blank? %) [phonetics pos]))))
