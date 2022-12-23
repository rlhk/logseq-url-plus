; CAUTION: ONLY allows pure fns to facilitate Node.js based TDD setup
(ns api
  (:require
   [cuerdas.core :as str]))

; TODO
(def settings-schema
  [{:key "TwitterAccessToken"
    :type "string"
    :title "Twitter Access Token"
    :description "See: https://developer.twitter.com/en/docs/authentication/oauth-2-0/bearer-tokens"
    :default ""}])

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
    (map #(identity {:content (edn->logseq-attrs %)}) data)
    {:content (edn->logseq-attrs data)}))

