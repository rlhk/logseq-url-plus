(ns word
  (:require
    [cuerdas.core :as str]))

(defn pos-group [group]
  (let [meanings (->> group :definitions (map :definition))
        pos (:partOfSpeech group)]
    (str
      (str/fmt "**%s** " pos)
      (str/join " "
        (->> meanings
             (map-indexed #(str/fmt "**%s.** %s", (inc %1), %2)))))))

(defn compact-def [w]
  (->> (first w) ; first item of API response array
       :meanings
       (map pos-group)
       (str/join "; ")))
