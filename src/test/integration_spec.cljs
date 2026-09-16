(ns integration-spec
  "End-to-end coverage of the slash-command pipeline.

  Runs the real `core/handle-slash-cmd` - token extraction, URL
  canonicalization, tracker stripping, fetching, templating and the block
  write - with only the Logseq host and the network faked. That is the layer
  where `core`, `ls` and the templates actually meet, and it had no coverage
  at all before 0.2.0."
  (:require
   [cljs.test :refer [deftest is testing async]]
   [clojure.string]
   [promesa.core :as p]
   [harness :as h]
   [config]
   [core]))

;; Route metadata lookups through the harness fixture. link-preview-js fetches
;; via its own transport, out of reach of the js/fetch stub, and `with-redefs`
;; is unusable here because it restores synchronously - long before the async
;; pipeline reaches the call.
(set! core/fetch-link-preview
      (fn [_] (p/resolved @h/link-preview-fixture)))

(defn- cmd
  "Look up a slash command from the real config by its :desc."
  [desc]
  (or (first (filter #(= desc (:desc %)) config/slash-commands))
      (throw (js/Error. (str "No such slash command: " desc)))))

(defn- run-cmd!
  "Run a command and call `f` once the pipeline settles."
  [command f]
  (-> (p/promise (core/handle-slash-cmd command))
      (p/then (fn [_] (f)))
      (p/catch (fn [e]
                 (is false (str "pipeline threw: " (or (.-stack e) e)))
                 (f)))))

(defn- start!
  "Install the doubles for one test."
  ([block-content] (start! block-content [] nil))
  ([block-content routes] (start! block-content routes nil))
  ([block-content routes link-preview]
   (h/setup! (if (map? block-content) block-content {:block-content block-content})
             routes)
   (h/set-link-preview! link-preview)))

(def ^:private page-meta
  #js {:title "Never Gonna Give You Up"
       :description "The official video"})

;; --------------------------------------------------------------- :meta path

(deftest meta-command-writes-title-and-fetches-once
  (async done
    (start! "see https://youtu.be/dQw4w9WgXcQ" [] page-meta)
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (testing "the block is rewritten with the fetched title, and the
                 youtu.be short URL is canonicalized"
         (is (= "see [Never Gonna Give You Up](https://www.youtube.com/watch?v=dQw4w9WgXcQ)"
                (second (h/first-op :update-block)))))
       (testing "a :meta command must not also hit the JSON API path - both
                 fetches used to run for every command"
         (is (zero? (h/op-count :fetch))))
       (done)))))

(deftest meta-command-escapes-hostile-title
  (async done
    (start! "https://example.com/p" []
            #js {:title "evil](javascript:alert(1)) type:: x"})
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (let [content (second (h/first-op :update-block))]
         (testing "remote text cannot break out of the markdown link"
           (is (re-find #"evil\\\]" content)))
         (testing "nor inject Logseq property syntax"
           (is (re-find #"type:\\:" content))))
       (done)))))

(deftest meta-command-reports-unreadable-metadata
  (async done
    (start! "https://example.com/p" [] nil)
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (is (zero? (h/op-count :update-block)) "must not write on failure")
       (is (re-find #"could not read metadata" (h/messages)))
       (done)))))

;; ---------------------------------------------------------------- :api path

(deftest api-json-command-writes-child-block
  (async done
    (start! "data https://api.example.com/posts/1"
            [["api.example.com" {:body {:id 1 :title "hello"}}]])
    (run-cmd!
     (cmd "URL+ API -> JSON Code")
     (fn []
       (let [child (second (h/first-op :insert-block))]
         (is (re-find #"```json" child))
         (is (re-find #"\"title\": \"hello\"" child)))
       (is (= 1 (h/op-count :fetch)) "exactly one request")
       (done)))))

(deftest api-accepts-content-type-without-parameters
  (async done
    (start! "https://api.example.com/posts/1"
            [["api.example.com" {:content-type "application/json" :body {:id 1}}]])
    (run-cmd!
     (cmd "URL+ API -> JSON Code")
     (fn []
       (testing "a bare `application/json` header was previously rejected as
                 invalid-json-response by the regex parser"
         (is (= 1 (h/op-count :insert-block))))
       (done)))))

(deftest api-surfaces-network-failure
  (async done
    (start! "https://api.example.com/posts/1"
            [["api.example.com" {:reject "ECONNREFUSED"}]])
    (run-cmd!
     (cmd "URL+ API -> JSON Code")
     (fn []
       (is (zero? (h/op-count :insert-block)))
       (testing "the failure must reach the user, not vanish as an unhandled
                 rejection"
         (is (re-find #"network-error" (h/messages))))
       (done)))))

(deftest api-surfaces-non-json-response
  (async done
    (start! "https://api.example.com/posts/1"
            [["api.example.com" {:content-type "text/html" :body "<html>"}]])
    (run-cmd!
     (cmd "URL+ API -> JSON Code")
     (fn []
       (is (zero? (h/op-count :insert-block)))
       (is (re-find #"invalid-json-response" (h/messages)))
       (done)))))

;; ------------------------------------------------------------- :api/define

(deftest define-command-appends-definition
  (async done
    (start! "the word prodigy"
            [["dictionaryapi.dev"
              {:body [{:word "prodigy"
                       :phonetics [{:text "/prod/"}]
                       :meanings [{:partOfSpeech "noun"
                                   :definitions [{:definition "A young person with exceptional talent."}]}]}]}]])
    (run-cmd!
     (cmd "URL+ Append Word Definition")
     (fn []
       (let [child (second (h/first-op :insert-block))]
         (is (re-find #"noun" child))
         (is (re-find #"exceptional talent" child)))
       (done)))))

(deftest define-command-distinguishes-outage-from-unknown-word
  (async done
    ;; dictionaryapi.dev is community-run; it returned HTTP 522 during real
    ;; testing. Reporting that as "no definition found" blames the word for the
    ;; service being down.
    (start! "prodigy"
            [["dictionaryapi.dev" {:status 522 :content-type "text/html" :body "<html>"}]])
    (run-cmd!
     (cmd "URL+ Append Word Definition")
     (fn []
       (is (zero? (h/op-count :insert-block)))
       (is (re-find #"service unavailable" (h/messages)))
       (is (re-find #"522" (h/messages)))
       (done)))))

(deftest define-command-reports-unknown-word
  (async done
    (start! "asdfqwerzxcv"
            [["dictionaryapi.dev" {:status 404 :body {:title "No Definitions Found"}}]])
    (run-cmd!
     (cmd "URL+ Append Word Definition")
     (fn []
       (is (zero? (h/op-count :insert-block)))
       (is (re-find #"no definition found" (h/messages)))
       (done)))))

;; -------------------------------------------------------- guard conditions

(deftest no-block-being-edited
  (async done
    (start! {:block-uuid nil :block-content nil} [] page-meta)
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (testing "getEditingBlockContent resolves to null outside an edit; that
                 nil used to reach a regex and throw"
         (is (zero? (h/op-count :update-block)))
         (is (re-find #"no block is being edited" (h/messages))))
       (done)))))

(deftest blank-block-content
  (async done
    (start! {:block-content "   "} [] page-meta)
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (is (zero? (h/op-count :update-block)))
       (is (re-find #"no token" (h/messages)))
       (done)))))

(deftest non-url-token-is-rejected
  (async done
    (start! "just a word" [] page-meta)
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (is (zero? (h/op-count :update-block)))
       (is (re-find #"invalid URL" (h/messages)))
       (done)))))

;; ------------------------------------------------------------ url handling

(deftest trackers-stripped-before-the-block-is-written
  (async done
    (start! "https://example.com/p?utm_source=x&keep=1#frag" []
            #js {:title "T"})
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (testing "utm_ stripped, kept param and fragment preserved"
         (is (= "[T](https://example.com/p?keep=1#frag)"
                (second (h/first-op :update-block)))))
       (done)))))

;; ------------------------------------------------- issue #20: caret targeting

(def ^:private a-meta #js {:title "A Title" :description "About A"})

(deftest caret-targets-the-url-it-is-on
  ;; The issue #20 regression test. Two URLs, cursor parked at the end of the
  ;; first: the first must be formatted and everything after it left alone.
  (async done
    (start! {:block-content "alpha https://a.com middle https://b.com omega"
             :cursor-pos    19}
            [] a-meta)
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (is (= "alpha [A Title](https://a.com) middle https://b.com omega"
              (second (h/first-op :update-block))))
       (done)))))

(deftest caret-inside-a-url-snaps-to-the-whole-url
  (async done
    (start! {:block-content "alpha https://a.com middle https://b.com omega"
             :cursor-pos    12}
            [] a-meta)
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (is (= "alpha [A Title](https://a.com) middle https://b.com omega"
              (second (h/first-op :update-block))))
       (done)))))

(deftest caret-at-end-is-unchanged
  ;; Same content and expectation as the :meta test above, but with the cursor
  ;; stated explicitly - the guarantee existing users rely on.
  (async done
    (start! {:block-content "see https://youtu.be/dQw4w9WgXcQ"
             :cursor-pos    32}
            [] page-meta)
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (is (= "see [Never Gonna Give You Up](https://www.youtube.com/watch?v=dQw4w9WgXcQ)"
              (second (h/first-op :update-block))))
       (done)))))

(deftest cursor-unavailable-falls-back-to-last-token
  ;; Three ways the caret can be unusable. None may regress today's behaviour.
  (async done
    (let [expected "see [Never Gonna Give You Up](https://www.youtube.com/watch?v=dQw4w9WgXcQ)"]
      (-> (p/do
            (p/create
             (fn [res _]
               (start! {:block-content "see https://youtu.be/dQw4w9WgXcQ" :cursor-pos nil}
                       [] page-meta)
               (run-cmd! (cmd "URL+ [title](url)")
                         (fn []
                           (is (= expected (second (h/first-op :update-block)))
                               "null cursor")
                           (res true)))))
            (p/create
             (fn [res _]
               (start! {:block-content "see https://youtu.be/dQw4w9WgXcQ" :cursor-pos :missing}
                       [] page-meta)
               (run-cmd! (cmd "URL+ [title](url)")
                         (fn []
                           (is (= expected (second (h/first-op :update-block)))
                               "cursor API absent")
                           (res true)))))
            (p/create
             (fn [res _]
               (start! {:block-content "see https://youtu.be/dQw4w9WgXcQ" :cursor-pos 9999}
                       [] page-meta)
               (run-cmd! (cmd "URL+ [title](url)")
                         (fn []
                           (is (= expected (second (h/first-op :update-block)))
                               "cursor out of range")
                           (res true))))))
          (p/then (fn [_] (done)))))))

(deftest caret-before-any-token-reports-and-writes-nothing
  ;; No URL anywhere, so the sole-URL rescue has nothing to offer and the
  ;; cursor really is the only signal. Contrast with the test below.
  (async done
    (start! {:block-content "alpha beta" :cursor-pos 0} [] a-meta)
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (is (re-find #"no URL or word before the cursor" (h/messages)))
       (is (zero? (h/op-count :update-block)) "nothing may be written")
       (done)))))

;; ------------------------------------------------------------- sole-URL rescue

(deftest sole-url-rescues-even-a-cursor-at-column-zero
  ;; With one URL in the block the cursor position cannot make it ambiguous,
  ;; so the rescue wins over the "nothing before the cursor" message.
  (async done
    (start! {:block-content "alpha https://a.com" :cursor-pos 0} [] a-meta)
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (is (= "alpha [A Title](https://a.com)"
              (second (h/first-op :update-block))))
       (done)))))

(deftest sole-url-is-used-even-when-the-cursor-is-elsewhere
  ;; One URL in the block and the cursor on a word: unambiguous, so use it
  ;; rather than failing with `invalid URL "details"`.
  (async done
    (start! {:block-content "see https://a.com for details" :cursor-pos 29} [] a-meta)
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (is (= "see [A Title](https://a.com) for details"
              (second (h/first-op :update-block))))
       (done)))))

(deftest rescue-does-not-fire-with-two-urls
  ;; Ambiguous, so the cursor must decide - here it is on neither URL, and the
  ;; command fails loudly rather than guessing.
  (async done
    (start! {:block-content "https://a.com and https://b.com then words" :cursor-pos 41} [] a-meta)
    (run-cmd!
     (cmd "URL+ [title](url)")
     (fn []
       (is (zero? (h/op-count :update-block)) "must not guess between two URLs")
       (done)))))

(deftest append-definition-is-never-hijacked-by-the-rescue
  ;; The conflict the rescue rule creates: this command wants a word, and a
  ;; blind rescue would define the URL instead.
  (async done
    (start! {:block-content "https://a.com prodigy" :cursor-pos 21}
            [["dictionaryapi.dev"
              {:body [{:word "prodigy"
                       :meanings [{:partOfSpeech "noun"
                                   :definitions [{:definition "A young genius."}]}]}]}]])
    (run-cmd!
     (cmd "URL+ Append Word Definition")
     (fn []
       (is (= "https://a.com prodigy #card" (second (h/first-op :update-block)))
           "the word is the target, not the URL")
       (done)))))

;; ------------------------------------------------- tail placement in the block

(deftest attrs-template-keeps-trailing-text-off-the-property-lines
  ;; `key:: value` lines have to own their lines. If the block's tail were
  ;; appended at the end of the rendered string it would land below them and
  ;; break the property parse - so it goes at the end of line 1 instead. This
  ;; test exists to stop that being "simplified" back to a plain append.
  (async done
    (start! {:block-content "alpha https://a.com middle" :cursor-pos 19} [] a-meta)
    (run-cmd!
     (cmd "URL+ Metadata -> Logseq Attributes")
     (fn []
       (let [written (second (h/first-op :update-block))
             lines   (clojure.string/split-lines written)]
         (is (= "alpha https://a.com middle" (first lines))
             "the tail belongs on line 1, beside the URL")
         (is (every? #(re-find #"^\w+:: " %) (remove clojure.string/blank? (rest lines)))
             "every line below line 1 is a property"))
       (done)))))

(deftest child-block-command-preserves-the-whole-block
  ;; For `%(but-last)s%(token)s` templates the parent text does not change at
  ;; all, so a mid-block target must round-trip the content byte-for-byte.
  (async done
    (let [content "alpha https://a.com middle https://b.com omega"]
      (start! {:block-content content :cursor-pos 19} [] a-meta)
      (run-cmd!
       (cmd "URL+ Metadata -> JSON Code")
       (fn []
         (is (= content (second (h/first-op :update-block)))
             "the block text must be untouched")
         (is (= 1 (h/op-count :insert-block)) "the child block is still written")
         (done))))))

;; ------------------------------------------------- URL+ All links in block

(defn- run-all-links! [f]
  (-> (p/promise (core/handle-all-links!))
      (p/then (fn [_] (f)))
      (p/catch (fn [e]
                 (is false (str "pipeline threw: " (or (.-stack e) e)))
                 (f)))))

(deftest all-links-converts-every-bare-url
  (async done
    (start! "alpha https://a.com middle https://b.com omega" [] a-meta)
    (run-all-links!
     (fn []
       (is (= "alpha [A Title](https://a.com) middle [A Title](https://b.com) omega"
              (second (h/first-op :update-block))))
       (is (re-find #"linked 2 of 2" (h/messages)))
       (done)))))

(deftest all-links-leaves-existing-markdown-links-alone
  ;; Wrapping an already-linked URL a second time would corrupt the block.
  (async done
    (start! "see [My Page](https://a.com) and https://b.com" [] a-meta)
    (run-all-links!
     (fn []
       (is (= "see [My Page](https://a.com) and [A Title](https://b.com)"
              (second (h/first-op :update-block))))
       (is (re-find #"linked 1 of 1" (h/messages)))
       (done)))))

(deftest all-links-reports-when-there-is-nothing-to-do
  (async done
    (start! "no links here at all" [] a-meta)
    (run-all-links!
     (fn []
       (is (re-find #"no URLs in this block" (h/messages)))
       (is (zero? (h/op-count :update-block)))
       (done)))))

(deftest all-links-keeps-unresolvable-urls-as-they-were
  ;; A partial result beats an aborted one, and the count has to be honest
  ;; about it. nil metadata is what fetch-link-preview yields on failure.
  (async done
    (start! "alpha https://a.com omega" [] nil)
    (run-all-links!
     (fn []
       (is (zero? (h/op-count :update-block)) "nothing resolved, so nothing written")
       (is (re-find #"linked 0 of 1" (h/messages)))
       (done)))))
