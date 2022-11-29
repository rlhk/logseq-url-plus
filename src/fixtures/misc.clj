(def logseq-blocks-a
  [{:content "a:: A\nb:: B"}
   {:content "c" :children [{:content "c1:: C1"}]}
   {:content "d" :children [{:content "something"}
                            {:content "simple"}]}
   {:content "e" :children [{:content "n:: N\nl:: L"}]}])

(def logseq-blocks-b
  [{:children [{:content "a:: A\nb:: B"}
               {:content "c" :children [{:content "c1:: C1"}]}
               {:content "d" :children [{:content "something"}
                                        {:content "simple"}]}
               {:content "e" :children [{:content "n:: N\nl:: L"}]}]}
   {:children [{:content "x:: X\ny:: Y\nz:: []"}]}])

(def edn-in-1
  [{:a "A"
    :b "B"
    :c [{:c1 "C1"}]
    :d ["something" "simple"]
    :e {:n "N" :l "L"}}
   {:x "X"
    :y "Y"
    :z []}])

(def edn-out-1
  [{:children [{:content "a:: A\nb:: B"}
               {:content "c" :children [{:content "c1:: C1"}]}
               {:content "d" :children [{:content "something"}
                                        {:content "simple"}]}
               {:content "e" :children [{:content "n:: N\nl:: L"}]}]}
   {:children [{:content "x:: X\ny:: Y\nz:: []"}]}])
    
(defn edn->logseq-blocks-x
  "Convert EDN data to Logseq block structure"
  [data]
  (cond
    (sequential? data), (map edn->logseq-blocks data)

    (map? data)
    (let [[attrs children] (attrs-and-children data)
          result [{:content (edn->logseq-attrs attrs)}]]
      (if (seq children) ; children is a map which might be empty
        (into result
              (for [[k v] children] ; v is non-empty and nested, empty vs are collected in attrs
                {:content (name k)
                 :children
                 (if (map? v)
                   (edn->logseq-blocks v)
                   (map edn->logseq-blocks v))}))
        result))

    :else {:content (if (keyword? data) (name data) data)}))