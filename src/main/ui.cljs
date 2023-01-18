(ns ui
  (:require 
   [rum.core :as rum]
   [clojure.pprint :refer [pprint]]
   [cuerdas.core :as str]
   [config :refer [db]]
   [clojure.math :as math]
   [feat.define]))

(defn to-fixed [number places]
  (.toFixed number places))

(def features
  [[:a "Apple"] [:b "Banana"] [:c "Banana"] [:d "daisy"] [:e "etch"]])

(rum/defc sin-table [aa]
  [:div {:class "p-4"}
   [:.overflow-x-auto
    [:table.table.table-compact.w-full
     [:thead
      [:tr
       (for [i (first aa)]
         [:th {:class "p-1"} i])]]
     [:tbody
      (for [i aa]
        [:tr
         (for [j i]
           [:td {:class "p-1"} j])])]]]])

(def dummy
  (for [i (range 10)]
    (map #(to-fixed (math/sin %) 3) (range 5))))

(rum/defc site-output []
  [:.w-full "3"])

(rum/defc api-output []
  [:.w-full "2"])

(rum/defc common-header [buttongrp]
  [:.w-full.p-4
   [:h1 "URL+"]
   [:div
    {:class "grid h-20 place-items-center"}
    [:input {:type "text"
             :class "input w-full"
             :placeholder "Type here"}]]
   #_[:pre (with-out-str (pprint (:slash-commands (rum/react db))))]
   [:div {:class "flex items-center justify-center overflow-x-hidden"}
    [:ul
     {:class "menu menu-horizontal bg-base-100 rounded-box"}
     (for [[k d] buttongrp]
       #_[:input {:id k :data-title d :type "radio" :name "fruit" :class "btn"}]
       [:li
        [:a {:key k} d]])]]]
  )

(rum/defc output-carousel [components]
  [:.w-64.carousel.rounded-box
   (for [c components] 
     [:.carousel-item.w-full
      c])])

(rum/defc confirmation < rum/reactive []
  [:.w-full.p-4
   [:.w-full.items-center.justify-center.flex.p-4
    [:button {:class "btn btn-sm btn-info"
              :on-click #(do (println "clicked...")
                             (js/logseq.hideMainUI))}
     "Confirm"]
    [:div.divider.divider-horizontal]
    [:button {:class "btn btn-outline btn-sm btn-outline"
              :on-click #(do (println "clicked...")
                             (js/logseq.hideMainUI))}
     "Cancel"]]])

#_{:clj-kondo/ignore [:unresolved-symbol]}
(rum/defc plugin-panel < rum/reactive []
  (println "Mounting logseq url+ advanced UI ...")
  [:main.fixed.inset-0.flex.items-center.justify-center.url-plus-backdrop
   [:div.url-plus-box
    {:class "flex flex-col border-opacity-50"}
    (common-header features)
    (sin-table dummy)
    [:textarea.textarea
     {:placeholder "bio"}]
    (output-carousel
     [(site-output) (api-output) (sin-table dummy) (sin-table dummy)])
    (confirmation)]])



(comment
  (in-ns 'ui)
  (js/logseq.showMainUI)
  (js/logseq.hideMainUI)
  (swap! db assoc :slash-commands {:name "Alice"})
  (swap! db assoc :slash-commands {:name "Bob" :gender :male})
  (rum/mount (plugin-panel) (.getElementById js/document "app")))
  