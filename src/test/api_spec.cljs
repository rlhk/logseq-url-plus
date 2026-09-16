(ns api-spec
  (:require
   [cljs.test :refer [deftest is are]]
   [api]))

(deftest formatting
  (are [in out] (= (api/edn->logseq-attrs in) out)
    {:title "Fox" :description "The brown fox."}
    "title:: Fox\ndescription:: The brown fox."

    ;; A multi-line value would otherwise truncate the attribute and spill the
    ;; remainder into block content.
    {:title "Fox" :description "Line one\nline two"}
    "title:: Fox\ndescription:: Line one line two"))

(deftest table-formatting
  ;; A pipe in a value would otherwise terminate the column early.
  (is (= "| a\\|b | c |" (api/md-table-row ["a|b" "c"])))
  (is (= "| one two |" (api/md-table-row ["one\ntwo"]))))

(deftest link-transform
  (is (=  ["GitHub: Let’s build from here" "https://github.com"]
          (api/md-link->label-and-url "[GitHub: Let’s build from here](https://github.com)")))
  (is (=  ["nice face" "https://face.com"]
          (api/md-link->label-and-url "[nice face](https://face.com)")))
  (is (=  [nil "https://face.com"]
          (api/md-link->label-and-url "https://face.com")))
  (is (=  ["nice face" "anything"]
          (api/md-link->label-and-url "[nice face](anything)")))
  ;; A URL containing parentheses must be captured whole; the old lazy pattern
  ;; stopped at the first ")" and left a stray bracket behind.
  (is (=  ["Dog" "https://en.wikipedia.org/wiki/Dog_(disambiguation)"]
          (api/md-link->label-and-url
           "[Dog](https://en.wikipedia.org/wiki/Dog_(disambiguation))")))
  (is (=  [nil nil] (api/md-link->label-and-url nil))))
