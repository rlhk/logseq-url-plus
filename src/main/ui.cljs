(ns ui
  (:require 
   [rum.core :as rum]
   [clojure.pprint :refer [pprint]]
   [cuerdas.core :as str]
   [config :refer [db]]))

#_{:clj-kondo/ignore [:unresolved-symbol]}
(rum/defc plugin-panel < rum/reactive []
  (println "Mounting logseq url+ advanced UI ...")
  [:main.fixed.inset-0.flex.items-center.justify-center.url-plus-backdrop
   [:div.url-plus-box
    [:div
     [:div.text "TODO..."]
     [:pre (with-out-str (pprint (:slash-commands (rum/react db))))]
     [:.flex.w-full.items-center.justify-center
      [:button {:class "btn btn-outline btn-sm btn-info"
                :on-click #(do (println "clicked...")
                               (js/logseq.hideMainUI))}
       "Confirm"]
      [:.divider.divider-horizontal]
      [:button {:class "btn btn-outline btn-sm btn-outline"
                :on-click #(do (println "clicked...")
                               (js/logseq.hideMainUI))}
       "Cancel"]]]]])

(comment
  (in-ns 'ui)
  (js/logseq.showMainUI)
  (js/logseq.hideMainUI)
  (swap! db assoc :slash-commands {:name "Alice"})
  (swap! db assoc :slash-commands {:name "Bob" :gender :male})
  (rum/mount (plugin-panel) (.getElementById js/document "app"))
  )