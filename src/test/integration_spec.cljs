(ns integration-spec
  "End-to-end coverage of the slash-command pipeline.

  Runs the real `core/handle-slash-cmd` - token extraction, URL
  canonicalization, tracker stripping, fetching, templating and the block
  write - with only the Logseq host and the network faked. That is the layer
  where `core`, `ls` and the templates actually meet, and it had no coverage
  at all before 0.2.0."
  (:require
   [cljs.test :refer [deftest is testing async]]
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
