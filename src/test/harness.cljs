(ns harness
  "Test doubles for the plugin's two external edges: the Logseq host and the
  network.

  `handle-slash-cmd` is exercised for real against these; only the edges are
  faked. Assertions are therefore made on the exact block content that would
  have been written to the graph."
  (:require [cuerdas.core :as str]))

;; ---------------------------------------------------------------- recording

(defonce calls (atom []))

(defn- record! [op & args]
  (swap! calls conj (into [op] args)))

(defn reset-calls! [] (reset! calls []))

(defn ops
  "Recorded arguments for `op`, in call order."
  [op]
  (->> @calls (filter #(= op (first %))) (map #(vec (rest %)))))

(defn op-count [op] (count (ops op)))

(defn first-op
  "Arguments of the first recorded `op`, or nil."
  [op]
  (first (ops op)))

(defn last-op
  "Arguments of the most recent recorded `op`, or nil."
  [op]
  (last (ops op)))

(defn messages
  "Every `showMsg` string shown, joined. The pipeline emits a \"Processing
  URL\" notice before any outcome, so assertions look across all of them."
  []
  (str/join " | " (map first (ops :show-msg))))

;; -------------------------------------------------- link-preview injection

(defonce link-preview-fixture
  ;; link-preview-js fetches through its own transport, out of reach of the
  ;; `js/fetch` stub, so `core/fetch-link-preview` is swapped for a reader of
  ;; this atom. `with-redefs` cannot be used: it restores synchronously, long
  ;; before the async pipeline reaches the call.
  (atom nil))

(defn set-link-preview! [v] (reset! link-preview-fixture v))

;; ------------------------------------------------------------- logseq stub

(defn install-logseq!
  "Install a fake `logseq` global that records every Editor/UI call.

  `:block-uuid nil` simulates no block being edited, which is what
  `getCurrentBlock` resolves to outside an editing context."
  [{:keys [block-uuid block-content settings]
    :or   {block-uuid "uuid-1" settings {}}}]
  (set! (.-logseq js/globalThis)
        #js {:settings (clj->js settings)
             :UI #js {:showMsg (fn [msg]
                                 (record! :show-msg (str msg))
                                 (js/Promise.resolve))}
             :Editor
             #js {:getCurrentBlock
                  (fn [] (js/Promise.resolve (when block-uuid #js {:uuid block-uuid})))
                  :getEditingBlockContent
                  (fn [] (js/Promise.resolve block-content))
                  :updateBlock
                  (fn [uuid content]
                    (record! :update-block uuid content)
                    (js/Promise.resolve))
                  :insertBlock
                  (fn [uuid content]
                    (record! :insert-block uuid content)
                    (js/Promise.resolve))
                  :insertBatchBlock
                  (fn [uuid blocks opts]
                    (record! :insert-batch-block uuid blocks opts)
                    (js/Promise.resolve))
                  :registerSlashCommand
                  (fn [desc handler] (record! :register-slash-command desc handler))}}))

;; -------------------------------------------------------------- fetch stub

(defn- fake-response
  [{:keys [status content-type body]
    :or   {status 200 content-type "application/json"}}]
  #js {:ok (< status 400)
       :status status
       :statusText (if (< status 400) "OK" "Error")
       :headers #js {:get (fn [h]
                            (when (= (str/lower (str h)) "content-type")
                              content-type))}
       :json (fn [] (js/Promise.resolve (clj->js body)))})

(defn install-fetch!
  "Install a fake `fetch` resolving `routes`: a vector of [match response],
  where match is a substring of the URL. `:reject` on a response simulates a
  transport-level failure. Unmatched URLs resolve to a 404."
  [routes]
  (set! (.-fetch js/globalThis)
        (fn [url & _]
          (let [url (str url)]
            (record! :fetch url)
            (if-let [[_ resp] (first (filter (fn [[m _]] (str/includes? url m)) routes))]
              (if (:reject resp)
                (js/Promise.reject (js/Error. (:reject resp)))
                (js/Promise.resolve (fake-response resp)))
              (js/Promise.resolve (fake-response {:status 404 :body {}})))))))

(defn setup!
  "Install both doubles and clear the call log."
  [opts routes]
  (reset-calls!)
  (install-logseq! opts)
  (install-fetch! routes))
