(ns ls 
  "Logseq plugin API interops."
  (:require
   [cuerdas.core :as str]
   [clojure.pprint :refer [pprint]]
   [util :as u :refer [records?]]
   [api]
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
  (println "URL+ Formatting block (and child) ...")
  (when block-content (update-block uuid, block-content))
  (when child-block-content (insert-block uuid, child-block-content )))

(defn reload-plugin [plugin-id]
  ;; In JS console: LSPluginCore.reload("logseq-url-plus")
  ;; cljs REPL runtime lives in an iframe. 
  ;; Thus `top ` required to call LSPluginCore in parent.
  (js-invoke js/top.LSPluginCore "reload" plugin-id))
