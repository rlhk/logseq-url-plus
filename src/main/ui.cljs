(ns ui
  (:require 
   [rum.core :as rum]
   [clojure.pprint :refer [pprint]]
   [cuerdas.core :as str]
   [config :refer [plugin-state]]
   [clojure.math :as math]
   [feat.define]))

(defn to-fixed [number places]
  (.toFixed number places))

(def features
  [[:website "Website"] [:api-endpoint "API Endpoint"] [:word "Word"]])

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

(rum/defc token [s]
  [:p
   {:class "text-white h-20 place-items-center"}
   s])

(rum/defc semantic-switch < rum/reactive [semantics]
  (let [active-semantics (:token-semantics (rum/react plugin-state))]
    [:div {:class "flex items-center justify-center overflow-x-hidden"}
     [:ul
      {:class "menu menu-horizontal menu-compact bg-base-100 rounded-box"}
      (for [[k d] semantics]
        [:li {:key k}
         [:a {:class (when (= active-semantics k) "active")
              :on-click #(swap! plugin-state assoc-in [:token-semantics] k)}
          d]])]]))

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
  (let [state (rum/react plugin-state)]
    (println (str state))
    [:main.fixed.inset-0.flex.items-center.justify-center.url-plus-backdrop
     [:div.url-plus-box
      {:class "flex flex-col border-opacity-50"}
      [:.w-full
       (token (:token state))
       (semantic-switch config/token-semantics (:token-semantics state))]
      (case (:token-semantics state)
        :api-endpoint (api-metadata {:name "api-endpoint"})
        :word (term-metadata {:name "word"})
        (website-metadata {:a "ape" :b "bean" :c "crazy"}))
      [:textarea.textarea
       {:placeholder "bio"}]
      (confirmation)]]))

(comment
  (in-ns 'ui)
  (js/logseq.showMainUI)
  (js/logseq.hideMainUI)
  (swap! plugin-state assoc :slash-commands {:name "Alice"})
  (swap! plugin-state assoc :slash-commands {:name "Bob" :gender :male})
  (rum/mount (plugin-panel) (.getElementById js/document "app")))
  