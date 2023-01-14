(ns ui
  (:require [rum.core :as rum]))

#_{:clj-kondo/ignore [:unresolved-symbol]}
(rum/defc plugin-panel []
  (println "Mounting logseq url+ advanced UI ...")
  [:main.fixed.inset-0.flex.items-center.justify-center
   [:div.url-plus-box
    [:div
     [:div.radial-progress {:style {"--value" "70"}} "70%"]
     [:div.text "Hi URL+ user!"]
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

