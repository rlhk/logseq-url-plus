(ns ui
  (:require [rum.core :as rum]))

#_{:clj-kondo/ignore [:unresolved-symbol]}
(rum/defc plugin-panel []
  (println "Mounting logseq url+ advanced UI ...")
  [:div
   [:div.text
    {:style {:background-color "lightgray"
             :color "darkgray"
             :font-family "system-ui"}}
    "Hi URL+ user!"]
   [:button {:class "btn btn-primary"
             :on-click #(do (println "clicked...")
                            (js/logseq.hideMainUI))}
    "Click Me!!"]])

