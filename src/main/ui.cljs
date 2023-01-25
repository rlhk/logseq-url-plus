(ns ui
  (:require 
   [rum.core :as rum]
   [clojure.math :as math]
   [config :refer [plugin-state]]
   [util :as u]
   [feat.define]))

(defn records? [data]
  (and
   (sequential? data)
   (map? (first data))))

(defn data->table [data]
  [:table.table.table-compact.w-full
   (cond 
     (map? data)
     [:<>
      [:thead [:tr [:th.text-xs "key"], [:th.text-xs "value"]]]
      [:tbody
       (for [[k v] data]
         [:tr [:td.text-xs (name k)], [:td.text-xs (str v)]])]]

     (records? data)
     (let [headers (keys (first data))]
       [:<>
        [:thead [:tr (for [h headers] [:th.text-xs (name h)])]]
        [:tbody
         (for [i data]
           [:tr (for [h headers] 
                  [:td.text-xs (str (get i h))])])]])

     :else [:tbody [:tr [:td "Invalid API response or unsupported data structure."]]])])


(rum/defc website-metadata [data]
  (data->table data))

(rum/defc api-metadata [data]
  [:.p-4.w-full
   (str data)])

(rum/defc word-metadata [data]
  [:table.table.table-compact.w-full
   [:tbody
    [:tr [:td
          (if (u/url? data)
            "The detected token is an URL."
            (str data))]]]])

(rum/defc token-input [t]
  [:.form-control
   [:label.input-group.input-group-xs
    [:span "Token"]
    [:input {:type "text"
             :read-only true
             :class "input input-bordered input-xs"
             :style {:width "100%"}
             :default-value t}]]])

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
  (let [active-semantics (:token-semantics (rum/react plugin-state))
        api-record-count (:api-record-count (rum/react plugin-state))]
    [:.tabs
     (for [[k d] semantics]
       [:.tab.tab-sm.tab-lifted.space-x-1
        {:key k :class (when (= active-semantics k) "tab-active")
         :on-click #(swap! plugin-state assoc-in [:token-semantics] k)}
        d
        (when (and (= k :api-endpoint) api-record-count)
          [:.badge.badge-xs.ml-2 (str api-record-count)])])]))

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
     [:div.url-plus-box {:class "card w-3/5 bg-base-100 shadow-xl"}
      [:.items-center.text-center.space-y-2
       (token-input (:token state))
       [:div.w-full.text-sm.text-left "What's the semantics of the token?"]
       (semantic-tabs config/token-semantics (:token-semantics state))
       [:.overflow-x-auto.max-h-80
        (case (:token-semantics state)
          :api-endpoint (website-metadata (:api-edn state))
          :word (word-metadata (:token state)) 
          (website-metadata (:meta-edn state)))]
       [:textarea.textarea.w-full
        {:placeholder "TODO: Custom template ..."}]
       [:.card-actions.justify-center
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
  