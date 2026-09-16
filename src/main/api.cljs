(ns api
  "APIs for URL+"
  (:require
   [clojure.pprint :refer [pprint]]
   [util :as u :refer [records?]]
   [cuerdas.core :as str]))

(defn md-link->label-and-url
  "Convert a markdown link to [label url]; returns [nil s] when `s` is not one.

  Delegates to util/str->md-link - the two carried the same regex in different
  shapes, and only one of them needed fixing for nested parentheses."
  [maybe-link]
  (if-let [{:keys [label link]} (u/str->md-link maybe-link)]
    [label link]
    [nil maybe-link]))

(defn- single-line
  "Collapse newlines so a value cannot break out of the construct holding it."
  [v]
  (-> (str v)
      (str/replace #"\s*\r?\n\s*" " ")
      str/trim))

(defn- attr-value
  "Render an attribute value.

  Logseq parses `key:: value` a line at a time, so an embedded newline - common
  in a fetched description - truncates the attribute and spills the remainder
  into block content."
  [v]
  (single-line v))

(defn- table-cell
  "Render a markdown table cell: single-line, with pipes escaped so a value
  cannot terminate the column early."
  [v]
  (-> (single-line v)
      (str/replace #"\|" "\\|")))

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
     (str/join "\n" (for [[k v] m] (str (name k) ":: " (attr-value v)))))))

(defn edn->logseq-blocks
  "Convert EDN data to Logseq attribute blocks"
  [data]
  (if (sequential? data)
    (map #(-> {:content (edn->logseq-attrs %)}) data)
    {:content (edn->logseq-attrs data)}))

(defn md-table-row 
  "Return string representation of a markdown table row."
  [data] 
  (str "| " (str/join (interpose " | " (map table-cell data))) " |"))

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
