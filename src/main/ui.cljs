(ns ui
  (:require 
   [rum.core :as rum]
   [clojure.pprint :refer [pprint]]
   [cuerdas.core :as str]
   [config :refer [db]]
   [clojure.math :as math]))

(def dummy
  (for [i (range 10)]
    (map #(math/sin %) (range 5))))

#_{:clj-kondo/ignore [:unresolved-symbol]}
(rum/defc plugin-panel < rum/reactive []
  (println "Mounting logseq url+ advanced UI ...")
  [:main.fixed.inset-0.flex.items-center.justify-center.url-plus-backdrop
   [:div.url-plus-box
    {:class "flex flex-col border-opacity-50"}
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
      (for [[k d] [[:a "Apple"] [:b "Banana"] [:c "Banana"]]]
        #_[:input {:id k :data-title d :type "radio" :name "fruit" :class "btn"}]
        [:li
         [:a {:key k} d]])]]
    [:div {:class "p-4 flex items-center justify-center overflow-x-hidden"}
     [:.overflow-x-auto
      [:table.table.table-compact.w-full
       [:thead
        [:tr
         (for [i (first dummy)]
           [:th {:class "p-1"} i])]]
       [:tbody
        (for [i dummy]
          [:tr
           (for [j i]
             [:td {:class "p-1"} j])])]]]]
    [:textarea.textarea
     {:placeholder "bio"}]
    [:.w-full.items-center.justify-center.flex.p-4
     [:button {:class "btn btn-sm btn-info"
               :on-click #(do (println "clicked...")
                              (js/logseq.hideMainUI))}
      "Confirm"]
     [:div.divider.divider-horizontal]
     [:button {:class "btn btn-outline btn-sm btn-outline"
               :on-click #(do (println "clicked...")
                              (js/logseq.hideMainUI))}
      "Cancel"]]]])



(comment
  (in-ns 'ui)
  (js/logseq.showMainUI)
  (js/logseq.hideMainUI)
  (swap! db assoc :slash-commands {:name "Alice"})
  (swap! db assoc :slash-commands {:name "Bob" :gender :male})
  (rum/mount (plugin-panel) (.getElementById js/document "app"))
  )