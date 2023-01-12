(ns ui
  (:require [rum.core :as rum]))

(rum/defc plugin-panel []
  (println "Mounting logseq url+ advanced UI ...")
  [:.content.logseq-url-plus-advanced 
   {:style {:background-color "lightgray" 
            :color "darkgray"
            :font-family "system-ui"}}
   "Hi URL+ user!"
   [:input {:type "button" 
            :class ""
            :on-click #(do (println "clicked...")
                           (js/logseq.hideMainUI))
            :value "Click Me!"}]
   ])

