(ns ui
  (:require 
   [rum.core :as rum]
   [config :refer [plugin-state]]
   [util :as u]
   [cuerdas.core :as str]
   [feat.define]))

(defn records? [data]
  (and (sequential? data), (map? (first data))))

(defn data-table [data]
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
     
     :else [:tbody 
            [:tr [:td 
                  (if (some? data) 
                    (str data) "No metadata/data or invalid format")]]])])

(rum/defc word-metadata [data]
  [:table.table.table-compact.w-full
   [:tbody
    [:tr [:td
          (if (u/url? data)
            "The selected token is not a word but an URL."
            (str data))]]]])

(rum/defc token-input [t]
  [:.form-control
   [:label.input-group.input-group-xs
    [:span "Token"]
    [:input.input.input-bordered.input-xs 
     {:type "text"
      :read-only true
      :placeholder (when (str/empty? t) "No token detected")
      :style {:width "100%"}
      :default-value t}]]])

(rum/defc semantic-tabs < rum/reactive [semantics]
  (let [{:keys [token token-semantics api-edn meta-edn api-record-count]} 
        (rum/react plugin-state)]
    [:.tabs
     (for [[k desc] semantics]
       [:.tab.tab-sm.tab-lifted.space-x-1
        {:key k
         :class (when (= token-semantics k) "tab-active")
         :on-click #(swap! plugin-state assoc :token-semantics k)}
        desc
        (when (and (= k :website), (not meta-edn))
          [:.badge.badge-error.badge-xs.ml-2 "!"])
        (when (= k :api-endpoint)
          (cond
            (not api-edn) [:.badge.badge-error.badge-xs.ml-2 "!"]
            (and (some? api-record-count), (not (zero? api-record-count)))
            [:.badge.badge-info.badge-xs.ml-2 (str api-record-count)]
            :else nil))
        (when (and (= k :word)
                   (u/url? token))
          [:.badge.badge-error.badge-xs.ml-2 "!"])])]))

#_{:clj-kondo/ignore [:unresolved-symbol]}
(rum/defc plugin-panel < rum/reactive []
  (println "Mounting Logseq URL+ UI ...")
  (let [state (rum/react plugin-state)]
    ;; (println (str state))
    [:main.fixed.inset-0.flex.items-center.justify-center.url-plus-backdrop
     [:div.url-plus-box {:class "card w-3/5 bg-base-100 shadow-xl"}
      [:.items-center.text-center.space-y-2
       (token-input (:token state))
       [:div.w-full.text-sm.text-left "Select token type:"]
       (semantic-tabs config/token-semantics (:token-semantics state))
       [:.overflow-x-auto.max-h-80
        (case (:token-semantics state)
          :api-endpoint (data-table (:api-edn state))
          :word (word-metadata (:token state)) 
          (data-table (:meta-edn state)))]
       [:textarea.textarea.w-full
        {:placeholder "TODO: Custom template ..."}]
       [:.card-actions.justify-center
        [:button.btn.btn-sm.btn-primary 
         {:on-click #(js/logseq.hideMainUI)} "Confirm"]
        [:button.btn.btn-sm.btn-ghost 
         {:on-click #(js/logseq.hideMainUI)} "Cancel"]]]]]))

(comment
  (in-ns 'ui)
  plugin-state  
  (js/logseq.showMainUI)
  (js/logseq.hideMainUI)
  (rum/mount (plugin-panel) (.getElementById js/document "app"))
  (swap! plugin-state assoc :slash-commands {:name "Alice"})
  (swap! plugin-state assoc :slash-commands {:name "Bob" :gender :male})
  ;; LSPluginCore.reload("logseq-url-plus")
  (.reload js/top.LSPluginCore "logseq-url-plus")
  (js-invoke js/top.LSPluginCore "reload" "logseq-url-plus")
  )
  