(ns ui
  (:require 
   [rum.core :as rum]
   [config :refer [plugin-state]]
   [util :as u]
   [cuerdas.core :as str]
   [feat.define]))

(defn records? [data]
  (and (sequential? data), (map? (first data))))

(defn data-table 
  ([data] (data-table nil data))
  ([caption data]
   [:table.table.table-compact.w-full
    (when caption 
      [:caption.p-1.text-sm.text-left.font-semibold.text-gray-900 caption])
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
                     (str data) "No metadata/data or invalid format")]]])]))

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

(def block-attrs [:block-content :block-content-before-token :token #_:url :token-label])
(rum/defc block-attrs-view [state]
  (data-table
   "Block & Token Attributes"
   (select-keys state block-attrs)))

(rum/defc website-view [state]
  [:<>
   [:.overflow-x-auto.max-h-64
    (data-table (:meta-edn state))]
   [:input.input.w-full.text-slate-400
    {:placeholder "Template for block content ..."
     :value (or (:block-template state) "")
     :on-change #(swap! plugin-state assoc :block-template (.. % -target -value))}]
   [:p.text-xs.text-left
    (str/fmt (or (:block-template state) "") 
             (merge (select-keys state block-attrs) (:meta-edn state)))]
   [:input.input.w-full.text-slate-400
    {:placeholder "Template for block child ..."
     :value (or (:block-child-template state) "")
     :on-change #(swap! plugin-state assoc :block-child-template (.. % -target -value))}]])

(rum/defc api-view [state]
  [:.overflow-x-auto.max-h-64
   (data-table (:api-edn state))])

(rum/defc word-view [state]
  (let [token (:token state)]
    [:.overflow-x-auto.max-h-64
     [:table.table.table-compact.w-full
      [:tbody
       [:tr [:td
             (if (u/http? token)
               "The token is a http(s) URL. Not a word."
               (str token))]]]]]))

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
                   (u/http? token))
          [:.badge.badge-error.badge-xs.ml-2 "!"])])]))

(rum/defc plugin-panel < rum/reactive []
  (println "Mounting Logseq URL+ UI ...")
  (let [state (rum/react plugin-state)]
    ;; (println (str state))
    [:main.fixed.inset-0.flex.items-center.justify-center.url-plus-backdrop
     [:div#url-plus-modal.url-plus-box.card.bg-base-100.shadow-xl {:class "w-3/5"}
      [:.items-center.text-center.space-y-2
       (token-input (:token state))
       [:.overflow-x-auto.max-h-80
        (block-attrs-view state)]
       [:div.w-full.text-sm.text-left.p-1.font-semibold.text-gray-900 "Token Metadata Insights"]
       (semantic-tabs config/token-semantics (:token-semantics state))
       (case (:token-semantics state)
         :api-endpoint (api-view state)
         :word (word-view state)
         (website-view state))
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
  (swap! plugin-state assoc :slash-commands {:name "Bob" :gender :male})
  (do
    (u/reload-plugin"logseq-url-plus")
    (js/console.clear)))