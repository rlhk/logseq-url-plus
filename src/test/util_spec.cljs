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

(deftest canonicalize-url
  (are [in out] (= (u/canonicalize-url in) out)
    ;; Short form -> canonical watch URL. This is the headline fix: the old
    ;; redirect handler rejected the youtu.be -> youtube.com hop outright.
    "https://youtu.be/dQw4w9WgXcQ"
    "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    ;; Share links carry an `si=` tracking param.
    "https://youtu.be/dQw4w9WgXcQ?si=AbCdEf123"
    "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

    ;; A deliberate timestamp must survive canonicalization.
    "https://youtu.be/dQw4w9WgXcQ?t=42"
    "https://www.youtube.com/watch?t=42&v=dQw4w9WgXcQ"

    "https://www.youtube.com/shorts/abc123XYZ"
    "https://www.youtube.com/watch?v=abc123XYZ"

    "https://www.youtube.com/live/abc123XYZ"
    "https://www.youtube.com/watch?v=abc123XYZ"

    "https://www.youtube.com/embed/abc123XYZ"
    "https://www.youtube.com/watch?v=abc123XYZ"

    ;; Mobile host is normalized; the video id is left alone.
    "https://m.youtube.com/watch?v=abc123XYZ"
    "https://www.youtube.com/watch?v=abc123XYZ"

    ;; Non-YouTube URLs pass through untouched.
    "https://example.com/a/b?x=1"
    "https://example.com/a/b?x=1"

    ;; Not a URL at all - returned unchanged rather than throwing.
    "just-a-word" "just-a-word"))

(deftest md-inline-escape
  (are [in out] (= (u/md-inline-escape in) out)
    "Plain title"                 "Plain title"
    ;; A hostile title must not be able to close the markdown link it sits in.
    "foo](javascript:alert(1))"   "foo\\](javascript:alert(1))"
    "[bracketed]"                 "\\[bracketed\\]"
    ;; Logseq property syntax would otherwise create spurious attributes.
    "type:: page"                 "type:\\: page"
    ;; Every occurrence must be escaped, not just the first.
    "a::b::c"                     "a:\\:b:\\:c"
    ;; Logseq macro syntax.
    "{{embed [[Home]]}}"          "{\\{embed \\[\\[Home\\]\\]}}"
    ;; Newlines break block structure and `key:: value` attribute lines.
    "line one\nline two"          "line one line two"
    "  padded  "                  "padded"
    nil                           nil))

(deftest content-type-parsing
  (are [in out] (= (:mime-type (u/content-type in)) out)
    "application/json; charset=utf-8" "application/json"
    ;; No parameter section: the old regex required a `;` and returned nil,
    ;; so valid JSON APIs were rejected as invalid-json-response.
    "application/json"                "application/json"
    "Application/JSON"                "application/json"
    "  application/json  "            "application/json")
  (is (= "utf-8" (:charset (u/content-type "application/json; charset=utf-8"))))
  (is (nil? (u/content-type nil)))
  (is (nil? (u/content-type "")))
  (are [in out] (= (u/json-response? in) out)
    "application/json; charset=utf-8" true
    "application/json"                true
    "Application/JSON"                true
    "text/json"                       true
    "application/vnd.api+json"        true
    "text/html; charset=utf-8"        false
    nil                               false
    ""                                false))

(deftest remove-url-trackers
  (is (= "https://example.com"
         (u/remove-url-trackers "https://example.com?utm_source=google&utm_medium=cpc")))
  ;; Fragment must survive: the old implementation split on `?` only, so the
  ;; fragment was swallowed together with the tracking parameter.
  (is (= "https://example.com/p#section"
         (u/remove-url-trackers "https://example.com/p?utm_source=a#section")))
  (is (= "https://example.com/p?keep=1#section"
         (u/remove-url-trackers "https://example.com/p?utm_source=a&keep=1#section")))
  ;; Non-utm_ trackers.
  (is (= "https://example.com/p"
         (u/remove-url-trackers "https://example.com/p?fbclid=abc&gclid=def&igshid=ghi")))
  ;; Case-insensitive prefix matching.
  (is (= "https://example.com/p"
         (u/remove-url-trackers "https://example.com/p?UTM_SOURCE=google")))
  ;; Percent-encoded values are passed through, not re-encoded.
  (is (= "https://example.com/p?q=a%20b%26c"
         (u/remove-url-trackers "https://example.com/p?q=a%20b%26c&utm_source=x")))
  ;; No query string at all.
  (is (= "https://example.com/p"
         (u/remove-url-trackers "https://example.com/p")))
  (is (= "https://example.com"
         (u/remove-url-trackers "https://example.com?utm_source=google&utm_medium=cpc&utm_campaign=summer")))
  (is (= "https://www.pinterest.com/pin/13651605113791129/?e_t=38c0d5cbcc4145b2811474ade9e79790"
         (u/remove-url-trackers "https://www.pinterest.com/pin/13651605113791129/?utm_campaign=category_rp&e_t=38c0d5cbcc4145b2811474ade9e79790&utm_source=31&utm_medium=2012&utm_content=13651605113791129&utm_term=1"))))