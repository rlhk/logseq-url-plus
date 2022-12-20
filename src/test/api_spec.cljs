(ns api-spec
  (:require
   [cljs.test :refer [deftest is are]]
   [api]))

(deftest formatting
  (are [in out] (= (api/edn->logseq-attrs in) out)
    {:title "Fox" :description "The brown fox."}
    "title:: Fox\ndescription:: The brown fox."))

(deftest link-transform
  (is (=  ["GitHub: Let’s build from here" "https://github.com"]
          (api/md-link->label-and-url "[GitHub: Let’s build from here](https://github.com)")))
  (is (=  ["nice face" "https://face.com"]
          (api/md-link->label-and-url "[nice face](https://face.com)")))
  (is (=  [nil "https://face.com"]
          (api/md-link->label-and-url "https://face.com")))
  (is (=  ["nice face" "anything"]
          (api/md-link->label-and-url "[nice face](anything)"))))

