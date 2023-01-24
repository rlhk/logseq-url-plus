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

(rum/defc token-input [s]
  [:.form-control
   [:label.input-group.input-group-xs
    [:span "Token"]
    [:input {:type "text"
             :read-only true
             :class "input input-bordered input-xs"
             :style {:width "100%"}
             :default-value s}]]])

(rum/defc token [s]
  [:div {:class "alert shadow-lg"}
   [:div 
    [:svg {:xmlns "http://www.w3.org/2000/svg"
           :class "stroke-current flex-shrink-0 h-6 w-6"
           :fill "none"
           :view-box "0 0 24 24"}
     [:path {:stroke-linecap "round"
             :stroke-linejoin "round"
             :stroke-width "2"
             :d "M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"}]]
    [:span s]]])

(rum/defc semantic-tabs < rum/reactive [semantics]
  (let [active-semantics (:token-semantics (rum/react plugin-state))]
    [:.tabs
     (for [[k d] semantics]
       [:a.tab.tab-sm.tab-lifted
        {:key k :class (when (= active-semantics k) "tab-active")
         :on-click #(swap! plugin-state assoc-in [:token-semantics] k)}
        d])]))

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
  [:.card-actions.justify-end
   [:button {:class "btn btn-sm btn-primary"
             :on-click #(do (println "clicked...")
                            (js/logseq.hideMainUI))}
    "Confirm"]
   [:button {:class "btn btn-sm btn-ghost"
             :on-click #(do (println "clicked...")
                            (js/logseq.hideMainUI))}
    "Cancel"]])

#_{:clj-kondo/ignore [:unresolved-symbol]}
(rum/defc plugin-panel < rum/reactive []
  (println "Mounting logseq url+ advanced UI ...")
  (let [state (rum/react plugin-state)]
    (println (str state))
    [:main.fixed.inset-0.flex.items-center.justify-center.url-plus-backdrop
     [:div.url-plus-box {:class "card w-96 bg-base-100 shadow-xl"}
      [:.items-center.text-center
       (token-input (:token state))
       [:hr {:style {:height "0.5em"}}]
       (semantic-tabs config/token-semantics (:token-semantics state))
       (case (:token-semantics state)
         :api-endpoint (api-metadata {:name "api-endpoint"})
         :word (term-metadata {:name "word"})
         (website-metadata {:a "ape" :b "bean" :c "crazy"}))
       [:textarea.textarea.w-full
        {:placeholder "Attributes ..."}]
       [:.card-actions.justify-end
        [:button {:class "btn btn-sm btn-primary"
                  :on-click #(do (println "clicked...")
                                 (js/logseq.hideMainUI))}
         "Confirm"]
        [:button {:class "btn btn-sm btn-ghost"
                  :on-click #(do (println "clicked...")
                                 (js/logseq.hideMainUI))}
         "Cancel"]]]]]))

(comment
  (in-ns 'ui)
  (js/logseq.showMainUI)
  (js/logseq.hideMainUI)
  (swap! plugin-state assoc :slash-commands {:name "Alice"})
  (swap! plugin-state assoc :slash-commands {:name "Bob" :gender :male})
  (rum/mount (plugin-panel) (.getElementById js/document "app")))
  