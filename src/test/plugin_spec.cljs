(ns plugin-spec
  (:require
    [cljs.test :refer [deftest is are testing]]
    [plugin]))

(deftest else-and-last
  "Separate the last term from everything else"
  (is (= ["" "world"]
         (plugin/else-and-last "world")))
  (is (= ["" "world"]
         (plugin/else-and-last " world")))
  (is (= ["hello good" "world"]
         (plugin/else-and-last "hello good world")))
  (is (= ["the new    fox is a red" "fox"]
         (plugin/else-and-last "the new    fox is a red fox  ")))
  (is (= [" The fox said:\n\r It's a new \n new" "hell!"]
         (plugin/else-and-last " The fox said:\n\r It's a new \n new hell!"))))

(deftest check-url
  (is (= true (plugin/url? "http://abc.com")))
  (is (= true (plugin/url? "https://www.abc.com/")))
  ; As of 20221020, the following case:
  ; Passed for logseq-plugin-automatic-url-title
  ; Failed for logseq-plugin-link-preview
  (is (= true (plugin/url? "https://blog.polygon.technology/nubank-taps-polygon-supernets-for-nucoin-token-launch-loyalty-program/")))
  (is (= false (plugin/url? "www.abc.com"))))

(deftest formatting
  (is (= "title:: Fox\ndescription:: The brown fox."
         (plugin/edn->logseq-attrs {:title "Fox" :description "The brown fox."}))))
