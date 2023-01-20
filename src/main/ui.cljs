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
  [[:website "Website"] [:api "API"] [:term "Term"]])

(rum/defc sin-table [tabledata]
  [:.p-4.w-full
   [:.overflow-x-auto
    [:table.table.table-compact.w-full
     [:thead
      [:tr
       (for [i (first tabledata)]
         [:th {:class "p-1"} i])]]
     [:tbody
      (for [i tabledata]
        [:tr
         (for [j i]
           [:td {:class "p-1"} j])])]]]])

(rum/defc website-metadata [data]
  [:.p-4.w-full
   (str data)])

(rum/defc api-metadata [data]
  [:.p-4.w-full
   (str data)])

(rum/defc term-metadata [data]
  [:.p-4.w-full
   (str data)])

(def dummy
  (for [i (range 10)]
    (map #(to-fixed (math/sin %) 3) (range 5))))

(rum/defc site-output []
  [:.w-full "3"])

(rum/defc api-output []
  [:.w-full "2"])

(rum/defc common-header [button-group]
  [:.w-full
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
     (for [[k d] button-group]
       #_[:input {:id k :data-title d :type "radio" :name "fruit" :class "btn"}]
       [:li
        [:a {:key k
             :on-click #(do
                          (println ":key" k)
                          (swap! db assoc-in [:ui :term-type] k))}
         d]])]]]
  )

(rum/defc output-carousel [components]
  [:.w-full.carousel.p-4.items-center
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
  (let [ui (:ui (rum/react db))]
    (println (str ui))
    [:main.fixed.inset-0.flex.items-center.justify-center.url-plus-backdrop
     [:div.url-plus-box
      {:class "flex flex-col border-opacity-50"}
      (common-header features)
      (case (:term-type ui)
        :api (api-metadata {:name "api"})
        :term (term-metadata {:name "term"})
        (website-metadata {:a "ape" :b "bean" :c "crazy"}))
      #_(sin-table dummy)
      [:textarea.textarea
       {:placeholder "bio"}]
      #_(output-carousel
         [(sin-table dummy) (sin-table dummy) (site-output) (api-output) (sin-table dummy)])
      (confirmation)]]))

(comment
  (in-ns 'ui)
  (js/logseq.showMainUI)
  (js/logseq.hideMainUI)
  (swap! db assoc :slash-commands {:name "Alice"})
  (swap! db assoc :slash-commands {:name "Bob" :gender :male})
  (rum/mount (plugin-panel) (.getElementById js/document "app")))
  