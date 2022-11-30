(ns plugin-spec
  (:require
   [cljs.test :refer [deftest is are]]
   [plugin]))

(deftest else-and-last
  (are [in out] (= (plugin/else-and-last in) out)
    "world", ["" "world"]
    " world", ["" "world"]
    "hello good world", ["hello good" "world"]
    "the new    fox is a red fox  ", ["the new    fox is a red" "fox"]
    " The fox said:\n\r It's a new \n new hell!", [" The fox said:\n\r It's a new \n new" "hell!"]))

(deftest check-url
  (is (= true (plugin/url? "http://abc.com")))
  (is (= true (plugin/url? "https://www.abc.com/")))
  ; As of 20221020, the following case:
  ; Passed for logseq-plugin-automatic-url-title
  ; Failed for logseq-plugin-link-preview
  (is (= true (plugin/url? "https://blog.polygon.technology/nubank-taps-polygon-supernets-for-nucoin-token-launch-loyalty-program/")))
  (is (= false (plugin/url? "www.abc.com"))))

(deftest formatting
  (are [in out] (= (plugin/edn->logseq-attrs in) out)
    {:title "Fox" :description "The brown fox."}
    "title:: Fox\ndescription:: The brown fox."))

(deftest edn-to-logseq-blocks
  (are [in out] (= (plugin/nested? in) out)
    [] false
    {} false
    "" false
    3  false
    "A String" false
    [:a :b] true
    {:a :apple} true)
  (are [in out] (= (plugin/attrs-and-children in) out)
    {:o "O" :p "P" :c [{:c1 "C1"}] :d []}
    [{:o "O" :p "P" :d []}, {:c [{:c1 "C1"}]}]

    {:a "A" :b "B" :c [] :d []}
    [{:a "A" :b "B" :c [] :d []}, {}])

  )

(deftest link-transform
  (is (=  ["nice face" "https://face.com"]
          (plugin/md-link->array "[nice face](https://face.com)")))
  (is (=  [nil "https://face.com"]
          (plugin/md-link->array "https://face.com")))
  (is (=  ["nice face" "anything"]
          (plugin/md-link->array "[nice face](anything)"))))