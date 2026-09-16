(ns util-spec
  (:require
   [cljs.test :refer [deftest is are testing]]
   [clojure.string :as cstr]
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

(deftest safe-fmt
  (is (= "hello world" (u/safe-fmt "hello %(who)s" {:who "world"})))
  (testing "a half-typed or malformed template must not throw - the inspector
            re-renders it on every keystroke"
    (is (string? (u/safe-fmt "broken %(" {:who "world"})))
    (is (string? (u/safe-fmt "unknown %(nope)s" {:who "world"})))
    (is (string? (u/safe-fmt nil {})))))

(deftest decode-html-content
  (are [in out] (= (u/decode-html-content in) out)
    "Tom &amp; Jerry"   "Tom & Jerry"
    "&lt;tag&gt;"       "<tag>"
    "it&#39;s"          "it's"
    "caf&#xe9;"         "caf\u00e9"
    "plain"             "plain"
    nil                 nil))

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
;; ---------------------------------------------------------------- caret split

(def ^:private else-and-last-inputs
  ;; Mirrors the inputs pinned in the `else-and-last` table above. Kept as a
  ;; def so the equivalence test below cannot quietly cover fewer cases than
  ;; the behaviour it is protecting.
  ["world"
   "  world"
   "hello good world"
   "the new    fox is a red fox  "
   " The fox said:\n\r It's a new \n new hell!"
   " Last term is link with space in label: [GitHub: Let’s build from here](https://github.com)"])

(deftest split-at-caret-degrades-to-else-and-last
  ;; The backward-compatibility guarantee for every existing user: with the
  ;; caret at the end of the block - which is where typing a URL then "/" puts
  ;; it - the new split must equal the old one exactly, with nothing trailing.
  (doseq [s else-and-last-inputs]
    (let [[before token] (u/else-and-last s)]
      (testing (str "end of block: " (pr-str s))
        (are [pos] (= {:before before :token token :after ""}
                      (u/split-at-caret s pos))
          nil            ; cursor API unavailable
          (count s)      ; caret at end
          js/NaN         ; degraded pos
          -1             ; degraded pos
          99999)))))     ; past the end

(deftest split-at-caret
  (are [content pos out] (= out (u/split-at-caret content pos))
    ;; Issue #20: two URLs, caret at the end of the first. The separator space
    ;; and everything after it must survive.
    "alpha https://a.com middle https://b.com" 19
    {:before "alpha " :token "https://a.com" :after " middle https://b.com"}

    ;; Caret strictly inside the first URL snaps right to the whole URL.
    "alpha https://a.com middle https://b.com" 12
    {:before "alpha " :token "https://a.com" :after " middle https://b.com"}

    ;; A markdown link whose label contains spaces is one token, which is why
    ;; this cannot be a whitespace split.
    "see [My Page](https://a.com) then more" 20
    {:before "see " :token "[My Page](https://a.com)" :after " then more"}

    ;; Parenthesised URL inside a link - the md-link-re lesson.
    "x [Dog](https://en.wikipedia.org/wiki/Dog_(disambiguation)) y" 30
    {:before "x " :token "[Dog](https://en.wikipedia.org/wiki/Dog_(disambiguation))" :after " y"}

    ;; Caret sitting in a run of whitespace. The run is NOT carried over on
    ;; both sides of the join: Logseq only opens its command menu when "/"
    ;; follows a space and leaves that space in the block, so preserving it as
    ;; well would widen the block by one space on every mid-block invocation.
    ;; The cost is that a block which genuinely had two spaces there comes back
    ;; with one - accepted, because double spaces carry no meaning in markdown
    ;; while a space that grows on every use is a visible wart.
    "alpha https://a.com  middle" 20
    {:before "alpha " :token "https://a.com" :after " middle"}

    ;; Caret at column 0 - no token to its left.
    "alpha https://a.com" 0
    {:before nil :token nil :after "alpha https://a.com"}

    ;; Multi-line: `before` spans the newline, `after` keeps the rest of line 2.
    "line one https://a.com\nline two https://b.com tail" 44
    {:before "line one https://a.com\nline two " :token "https://b.com" :after " tail"}))

(deftest split-at-caret-is-lossless
  ;; No non-whitespace character may ever be dropped. This matters more than
  ;; usual: for the first time the plugin rewrites blocks that have text AFTER
  ;; the token, and silently deleting it would be far worse than issue #20.
  (let [strip #(cstr/replace (or % "") #"\s+" "")]
    (doseq [[content pos] [["alpha https://a.com middle https://b.com" 19]
                           ["alpha https://a.com middle https://b.com" 12]
                           ["see [My Page](https://a.com) then more" 20]
                           ["alpha https://a.com  middle" 20]
                           ["alpha https://a.com" 0]
                           ["line one https://a.com\nline two https://b.com tail" 44]
                           ["trailing spaces here   " 23]]]
      (let [{:keys [before token after]} (u/split-at-caret content pos)]
        (is (= (strip content) (strip (str before token after)))
            (str "lost characters at pos " pos " of " (pr-str content)))))))

(deftest splice-after-first-line
  ;; Single-line templates: same as appending.
  (is (= "see [T](u) rest"
         (u/splice-after-first-line "see [T](u)" " rest" 4)))
  ;; Attribute templates emit `key:: value` lines that must own their lines, so
  ;; the tail belongs at the end of line 1, not below the properties.
  (is (= "see [T](u) rest\ntitle:: T\n"
         (u/splice-after-first-line "see [T](u)\ntitle:: T\n" " rest" 4)))
  ;; A `before` prefix that itself contains newlines must not splice into the
  ;; user's first line.
  (is (= "para one\npara two [T](u) rest\ntitle:: T"
         (u/splice-after-first-line "para one\npara two [T](u)\ntitle:: T" " rest" 9)))
  ;; Nothing to splice.
  (is (= "unchanged" (u/splice-after-first-line "unchanged" "" 0)))
  (is (= "unchanged" (u/splice-after-first-line "unchanged" nil 0)))
  ;; Out-of-range `from` is clamped rather than throwing.
  (is (= "abc!" (u/splice-after-first-line "abc" "!" 9999))))

(deftest url-spans
  (is (= [[6 19 "https://a.com"] [27 40 "https://b.com"]]
         (u/url-spans "alpha https://a.com middle https://b.com")))
  ;; A URL already inside a markdown link is skipped, so the convert-all
  ;; command cannot wrap it twice.
  (is (= [[29 42 "https://b.com"]]
         (u/url-spans "see [My Page](https://a.com) https://b.com")))
  ;; Sentence punctuation is not part of the URL.
  (is (= [[6 19 "https://a.com"]]
         (u/url-spans "visit https://a.com.")))
  ;; ...but a URL's own parentheses are.
  (is (= "https://en.wikipedia.org/wiki/Dog_(disambiguation)"
         (nth (first (u/url-spans "see https://en.wikipedia.org/wiki/Dog_(disambiguation) ok")) 2)))
  ;; A closing paren from surrounding prose is not.
  (is (= "https://a.com"
         (nth (first (u/url-spans "(see https://a.com)")) 2)))
  (is (= [] (u/url-spans "no links here")))
  (is (= [] (u/url-spans nil))))
