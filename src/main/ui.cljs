(ns ui
  (:require 
   [rum.core :as rum]
   [clojure.pprint :refer [pprint]]
   [cuerdas.core :as str]
   [config :refer [db]]))

#_{:clj-kondo/ignore [:unresolved-symbol]}
(rum/defc plugin-panel < rum/reactive []
  (println "Mounting logseq url+ advanced UI ...")
  [:main.fixed.inset-0.flex.items-center.justify-center
   [:div.url-plus-box
    [:div
     [:div.text "Todo..."]
     [:pre (with-out-str (pprint (:slash-commands (rum/react db))))]
     [:.flex.w-full
      [:button {:class "btn btn-outline btn-sm btn-info"
                :on-click #(do (println "clicked...")
                               (js/logseq.hideMainUI))}
       "Confirm"]
      [:.divider.divider-horizontal]
      [:button {:class "btn btn-outline btn-sm btn-outline"
                :on-click #(do (println "clicked...")
                               (js/logseq.hideMainUI))}
       "Cancel"]]]]])

