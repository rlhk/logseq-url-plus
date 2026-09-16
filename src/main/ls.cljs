(ns ls
  "Interop with the Logseq plugin API.

  Every accessor resolves `js/logseq` at call time rather than capturing its
  methods at namespace load. Two reasons:

  1. Calling through the owning object keeps `this` bound. The previous
     `(def show-msg js/logseq.UI.showMsg)` form detached the method and only
     worked because @logseq/libs happens to hand back bound proxies - the
     comment block below records that it already failed for top-level methods.
  2. It lets this namespace load where the `logseq` global does not exist yet,
     which is what makes the integration harness possible under Node.

  Importing @logseq/libs (which installs the global as a side effect) is the
  entry namespace's job, not this one's."
  (:require
   [promesa.core :as p]
   [util :as u :refer [devlog]]))

(defn- editor [] (.-Editor js/logseq))

(defn show-msg [msg] (.showMsg (.-UI js/logseq) msg))
(defn get-current-block [] (.getCurrentBlock (editor)))
(defn get-editing-block-content [] (.getEditingBlockContent (editor)))
(defn update-block [uuid content] (.updateBlock (editor) uuid content))
(defn insert-block [uuid content] (.insertBlock (editor) uuid content))
(defn insert-batch-block [uuid blocks opts] (.insertBatchBlock (editor) uuid blocks opts))
(defn register-slash-command [desc handler] (.registerSlashCommand (editor) desc handler))

;; Top level Logseq methods have to be called directly.
;; Defining in any ns won't work
;; (def ready js/logseq.ready)
;; (def use-settings-schema js/logseq.useSettingsSchema)
;; (def show-main-ui js/logseq.showMainUI)
;; (def toggle-main-ui js/logseq.toggleMainUI)
;; (def hide-main-ui js/logseq.hideMainUI)
;; (def provide-model js/logseq.provideModel)
;; (def set-main-ui-inline-style js/logseq.setMainUIInlineStyle)
;; (def show-settings-ui js/logseq.showSettingsUI)

(defn- hide-main-ui! []
  (js/logseq.hideMainUI (clj->js {:restoreEditingCursor true})))

(defn register-js-events []
  (js/document.addEventListener
   "keydown"
   (fn [e]
     ;; `keyCode` is deprecated; `key` is the modern equivalent.
     (when (= (.-key e) "Escape")
       (hide-main-ui!)
       ;; Only swallow the key we actually handled. The previous version
       ;; called stopPropagation on every keydown.
       (.stopPropagation e)))
   false)
  (js/document.addEventListener
   "click"
   (fn [e]
     (let [target (.-target e)]
       ;; `.closest` is an Element method; a click landing on a text node
       ;; would otherwise throw.
       (when (and target (= 1 (.-nodeType target))
                  (= target (.closest target ".url-plus-backdrop")))
         (hide-main-ui!))))))

(defn format-block-and-child
  "Update a block, then optionally append a child block.

  Returns a promise. `updateBlock` is asynchronous; the previous version
  discarded its promise and fired `insertBlock` immediately, racing the
  update against the insert."
  [uuid block-content child-block-content]
  (devlog "Formatting block (and child) ...")
  (p/let [_ (when block-content (update-block uuid block-content))]
    (when child-block-content (insert-block uuid child-block-content))))

(defn fetch-api
  "Fetch and parse a JSON API response. Accepts optional auth headers.

  Always returns a promise. On failure it resolves to a JS object of the shape
  {:error <kind> :message ...} rather than rejecting, so callers can branch on
  the result instead of relying on an unhandled rejection.

  The previous version logged network errors and returned nil from its
  `p/catch`, then dereferenced that nil for `.headers` - a TypeError that
  escaped `handle-slash-cmd` with no user-facing message at all."
  [url auth]
  (if-not (u/http? url)
    (p/resolved (clj->js {:error "invalid-url" :message (str url)}))
    (do
      (devlog "Fetching API:" url)
      (-> (p/promise (js/fetch url (when auth (clj->js {:headers auth}))))
          (p/then
           (fn [res]
             (let [ct (.get (.-headers res) "Content-Type")]
               (cond
                 (not (.-ok res))
                 (clj->js {:error "http-error"
                           :status (.-status res)
                           :message (str (.-status res) " " (.-statusText res))})

                 (not (u/json-response? ct))
                 (clj->js {:error "invalid-json-response"
                           :message (str "Content-Type: " ct)})

                 :else (.json res)))))
          (p/catch
           (fn [err]
             (devlog "Fetch failed:" err)
             (clj->js {:error "network-error"
                       :message (or (some-> err .-message) (str err))})))))))

(defn api-error
  "Return the :error string from a fetch-api result, or nil when it succeeded."
  [api-edn]
  (when (map? api-edn) (:error api-edn)))

(defn reload-plugin [plugin-id]
  ;; In JS console: LSPluginCore.reload("logseq-url-plus")
  ;; Since cljs REPL runtime lives in an iframe. 
  ;; Use `top.LSPluginCore` in the parent window.
  (js-invoke js/top.LSPluginCore "reload" plugin-id))

(comment
  (let [token "wall"
        good-url (str "https://api.dictionaryapi.dev/api/v2/entries/en/" token)
        good-url2 "https://jsonplaceholder.typicode.com/posts/1"
        bad-url (str "https://api.dictionaryapi.dev/api/v2/entries/en/")
        res (fetch-api #_good-url #__good-url2 bad-url nil)]
    (p/then res #(js/console.log %))))
