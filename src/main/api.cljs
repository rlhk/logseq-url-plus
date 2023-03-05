; CAUTION: ONLY allows pure fns to facilitate Node.js based TDD setup
(ns api
  "APIs for URL+"
  (:require
   [clojure.pprint :refer [pprint]]
   [util :as u :refer [records?]]
   [cuerdas.core :as str]))

(defn md-link->label-and-url
  "Convert markdown link to [label, url], return input as it is if not a markdown link."
  [maybe-link]
  (let [output (re-find #"\[(.*?)\]\((.*?)\)" maybe-link)]
    (if output (rest output), [nil maybe-link])))

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
    (map #(-> {:content (edn->logseq-attrs %)}) data)
    {:content (edn->logseq-attrs data)}))

(defn md-table-row 
  "Return string representation of a markdown table row."
  [data] 
  (str "| " (str/join (interpose " | " data)) " |"))

(defn md-table-header 
  "Return string representation of a markdown table header with the separator."
  [headers]
  (str/join "\n"
            [(md-table-row headers)
             (md-table-row (repeat (count headers) " ----- "))]))

(defn md-table 
  "Return markdown table string from data shape of a map or a vector of maps."
  [data]
  (cond
    (map? data)
    (str/join "\n"
     (into [(md-table-header ["KEY" "VALUE"])]
           (for [[k v] data] (md-table-row [(name k) (str v)]))))

    (records? data)
    (let [headers (keys (first data))]
      (str/join "\n"
       (into [(md-table-header (->> headers (map (comp str/upper name))))]
             (for [i data] (md-table-row (for [h headers] (get i h)))))))

    :else (str data)))

(defn md-data-block [data format]
  (case format
    :logseq-attrs (str "\n" (api/edn->logseq-attrs data))
    :json (str/fmt "```json\n%s\n```" (js/JSON.stringify (clj->js data) nil 2))
    :table (md-table data)
    ;; Default
    (str/fmt "```edn\n%s```" (with-out-str (pprint data)))))

;; Plugin app state management
