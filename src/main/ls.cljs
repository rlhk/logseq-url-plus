(ns ls 
  "Interops with Logseq plugin API "
  (:require
   [promesa.core :as p]
   [api]
   [util :as u :refer [devlog]]
   ["@logseq/libs"]))

(def show-msg js/logseq.UI.showMsg)
(def get-current-block js/logseq.Editor.getCurrentBlock)
(def get-editing-block-content js/logseq.Editor.getEditingBlockContent)
(def update-block js/logseq.Editor.updateBlock)
(def insert-block js/logseq.Editor.insertBlock)
(def insert-batch-block js/logseq.Editor.insertBatchBlock)
(def register-slash-command js/logseq.Editor.registerSlashCommand)

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

(defn register-js-events []
  (js/document.addEventListener
   "keydown"
   #(do
      (when (= (.-keyCode %) 27) (js/logseq.hideMainUI (clj->js {:restoreEditingCursor true})))
      (.stopPropagation %))
   false)
  (js/document.addEventListener
   "click"
   #(let [clicked (.-target %)
          backdrop (.closest clicked ".url-plus-backdrop")]
      (when (= clicked backdrop)
        (js/logseq.hideMainUI (clj->js {:restoreEditingCursor true}))))))

(defn format-block-and-child
  [uuid block-content child-block-content]
  (devlog "Formatting block (and child) ...")
  (when block-content (update-block uuid, block-content))
  (when child-block-content (insert-block uuid, child-block-content )))

(defn fetch-api
  "Return JSON response from an API URL.
  Accepts optional authentication info.
  TODO: Error handling and research established libs ..."
  [url auth]
  (if (u/http? url)
    (do
      (devlog "Fetching API:" url)
      (p/let [api-res  (-> (p/promise (js/fetch url (when auth (clj->js {:headers auth}))))
                           (p/then   #(-> %))
                           (p/catch  #(js/console.log %)))
              json?    (u/json-response? (.get (.-headers api-res) "Content-Type"))]
        (if json?
          (.json api-res)
          (clj->js {:error "invalid-json-response"}))))
    (clj->js {:error "invalid-url"})))

(defn reload-plugin [plugin-id]
  ;; In JS console: LSPluginCore.reload("logseq-url-plus")
  ;; cljs REPL runtime lives in an iframe. 
  ;; Thus `top ` required to call LSPluginCore in parent.
  (js-invoke js/top.LSPluginCore "reload" plugin-id))

(comment
  (let [token "wall"
        good-url (str "https://api.dictionaryapi.dev/api/v2/entries/en/" token)
        good-url2 "https://jsonplaceholder.typicode.com/posts/1"
        bad-url (str "https://api.dictionaryapi.dev/api/v2/entries/en/")
        res (fetch-api #_good-url #__good-url2 bad-url nil)]
    (p/then res #(js/console.log %))))
