(ns util-spec
  (:require
   [cljs.test :refer [deftest is are]]
   [util :as u]))

(deftest utils
  (are [in out] (= (u/nested? in) out)
    [] false
    {} false
    "" false
    3  false
    "A String" false
    [:a :b] true
    {:a :apple} true)
  (are [in out] (= (u/attrs-and-children in) out)
    {:o "O" :p "P" :c [{:c1 "C1"}] :d []}
    [{:o "O" :p "P" :d []}, {:c [{:c1 "C1"}]}]

    {:a "A" :b "B" :c [] :d []}
    [{:a "A" :b "B" :c [] :d []}, {}]))

(deftest check-url
  (is (= true (u/url? "http://abc.com")))
  (is (= true (u/url? "https://www.abc.com/")))
  ; As of 20221020, the following case:
  ; Passed for logseq-plugin-automatic-url-title
  ; Failed for logseq-plugin-link-preview
  (is (= true (u/url? "https://blog.polygon.technology/nubank-taps-polygon-supernets-for-nucoin-token-launch-loyalty-program/")))
  (is (= false (u/url? "www.abc.com"))))

(deftest else-and-last
  (are [in out] (= (u/else-and-last in) out)
    "world", ["" "world"]
    "  world", ["  " "world"]
    "hello good world", ["hello good " "world"]
    "the new    fox is a red fox  ", ["the new    fox is a red " "fox"]
    " The fox said:\n\r It's a new \n new hell!", [" The fox said:\n\r It's a new \n new " "hell!"]

    " Last term is link with space in label: [GitHub: Let’s build from here](https://github.com)" 
    [" Last term is link with space in label: " "[GitHub: Let’s build from here](https://github.com)"]))

(deftest remove-url-trackers
  (is (= "https://example.com"
         (u/remove-url-trackers "https://example.com?utm_source=google&utm_medium=cpc")))
  (is (= "https://example.com"
         (u/remove-url-trackers "https://example.com?utm_source=google&utm_medium=cpc&utm_campaign=summer")))
  (is (= "https://www.pinterest.com/pin/13651605113791129/?e_t=38c0d5cbcc4145b2811474ade9e79790"
         (u/remove-url-trackers "https://www.pinterest.com/pin/13651605113791129/?utm_campaign=category_rp&e_t=38c0d5cbcc4145b2811474ade9e79790&utm_source=31&utm_medium=2012&utm_content=13651605113791129&utm_term=1"))))