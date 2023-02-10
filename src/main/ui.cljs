(ns ui
  (:require 
   [rum.core :as rum]
   [cuerdas.core :as str]
   [config :refer [plugin-state block-attrs]]
   [util :as u :refer [target-value target-checked records?]]
   [api :refer [md-data-block]] 
   [ls]
   [feat.define]))

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
    [:span.font-semibold "Token"]
    [:input.input.input-bordered.input-xs 
     {:type "text"
      :read-only true
      :placeholder (when (str/empty? t) "No token detected")
      :style {:width "100%"}
      :default-value t}]]])

(rum/defc block-attrs-view [state]
  (data-table
   "Block & Token Attributes"
   (select-keys state block-attrs)))

(rum/defc metadata-format-option [state]
  [:.flex
   (for [[k v] config/metadata-formats]
     [:label.label.cursor-pointer.w-max.mr-2
      {:key k}
      [:input.radio.radio-xs.mr-2
       {:type "radio"
        :value k
        :name "child-block-metadata-format"
        :checked (= k (-> state :option :child-block-format))
        :on-change #(swap! plugin-state assoc-in [:option :child-block-format] (str/keyword (target-value %)))}]
      [:span.label-text.text-xs v]])])

(rum/defc template-editor
  [state & {:keys [template-key template-type label placeholder class]}]
  [:.form-control {:class class}
   (when label [:label.label.w-max 
                {:class (when (= template-type :select) "cursor-pointer")}
                (when (= template-type :select)
                  [:input.checkbox.checkbox-warning.checkbox-xs.mr-2 
                   {:type "checkbox" :name "r0"
                    :checked (-> state :option :append-child-block?)
                    :on-change #(swap! plugin-state assoc-in [:option :append-child-block?] (target-checked %))}])
                [:span.label-text label]])
   (case template-type
     :input
     [:input.input.input-info.input-accent.input-sm.w-full
      {:placeholder placeholder
       :value (get state template-key "")
       :on-change #(swap! plugin-state assoc template-key (target-value %))}]
     :select
     (if (-> state :option :append-child-block?) (metadata-format-option state))
     #_[:label.label.cursor-pointer
        [:span.label-text "Red"]
        [:input.checkbox.checkbox-xs {:type "checkbox" :name "r0"}]]

     (str ":template-type " template-type " to be implemented ..."))])

(rum/defc content-preview
  [state & {:keys [template-key class]}]
  [:p.text-xs.text-left.border-dotted.border-slate-500
   {:class class}
   "▶️ "
   (case template-key
     :block-template
     (str/fmt (get state template-key "")
              (merge (select-keys state block-attrs) (get state :meta-edn)))
     :child-template 
     (str "NOTE: Metadata/data will be rendered in child block as: " 
          (get config/metadata-formats (-> state :option :child-block-format)))
     (str ":template-type " template-key " to be implemented ..."))])

(rum/defc website-view [state]
  [:<>
   [:.overflow-x-auto.max-h-60
    (data-table (:meta-edn state))]
   ])

(rum/defc template-view [{:keys [option] :as state}]
  (let [{:keys [semantics]} option]
    [:<>
     [:p.text-left.text-sm.font-semibold "Templates"]
     (template-editor state
                      :template-key :block-template
                      :template-type :input
                      :placeholder "Input template for block content ..."
                      :label "Block content template"
                      :class "w-full")
     (template-editor state
                      :template-key :child-template
                      :template-type :select
                      :label (str/fmt 
                              "Append formatted %s metadata/data as child block" 
                              (get config/token-semantics semantics))
                      :class "w-full pl-4")
     [:p.text-left.text-sm.font-semibold "Content Preview"]
     [:.w-full.border-dashed.border.border-y-indigo-500
      (content-preview state
                       :template-key :block-template
                       :class "w-full")
      (when (-> state :option :append-child-block?)
        (content-preview state
                         :template-key :child-template
                         :class "w-full pl-4"))]]))

(rum/defc api-view [state]
  [:.overflow-x-auto.max-h-60
   (data-table (:api-edn state))])

(rum/defc word-view [state]
  (let [token (:token state)]
    [:.overflow-x-auto.max-h-60
     [:table.table.table-compact.w-full
      [:tbody
       [:tr [:td
             (cond 
               (u/http? token) "The token is a http(s) URL. Not a word."
               (u/md-link? token) "The token is a markdown link. Not a word."
               :else (str token))]]]]]))

(rum/defc semantic-tabs
  [{:keys [token option api-edn meta-edn api-record-count]} all-semantics]
  (let [{:keys [semantics]} option
        issue-indicator [:.ml-2 "😓"]]
    [:.tabs
     (for [[k desc] all-semantics]
       [:.tab.tab-sm.tab-lifted.space-x-1
        {:key k
         :class (when (= semantics k) "tab-active")
         :on-click #(swap! plugin-state assoc-in [:option :semantics] k)}
        desc
        (when (and (= k :website), (not meta-edn)) issue-indicator)
        (when (= k :api)
          (cond
            (not api-edn) issue-indicator
            (and (some? api-record-count), (not (zero? api-record-count)))
            [:.badge.badge-info.badge-xs.ml-2 (str api-record-count)]
            :else nil))
        (when (and (= k :word)
                   (or (u/http? token), (u/md-link? token)))
          issue-indicator)])]))

(defn handle-action [state]
  (when-let [block-uuid (-> state :block :uuid)]
    (case (-> state :option :semantics)
      :website
      (do
        (println "URL+ Handle semantics: :website")
        (ls/format-block-and-child
         block-uuid
         (when-let [block-template (:block-template state)]
           (str/fmt block-template (merge (select-keys state block-attrs) (:meta-edn state))))
         (let [{:keys [append-child-block? child-block-format]} (:option state)]
           (when append-child-block? (md-data-block (:meta-edn state) child-block-format)))))
      :api
      (do
        (println "URL+ Handle semantics: :api") 
        (ls/format-block-and-child
         block-uuid
         (when-let [block-template (:block-template state)]
           (str/fmt block-template (merge (select-keys state block-attrs) (:api-edn state))))
         (let [{:keys [append-child-block? child-block-format]} (:option state)]
           (when append-child-block? (md-data-block (:api-edn state) child-block-format)))))
      :word
      (do
        (println "URL+ Handle semantics: :word"))
      (println "action: default")))
  (js/logseq.hideMainUI))

(rum/defc plugin-panel < rum/reactive []
  (println "Mounting URL+ UI ...")
  (tap> @plugin-state)
  (let [state (rum/react plugin-state)]
    ;; (println (str state))
    [:main.fixed.inset-0.flex.items-center.justify-center.url-plus-backdrop
     [:#url-plus-modal.url-plus-box.card.bg-base-100.shadow-xl {:class "w-3/5"}
      [:.items-center.text-center.space-y-2
       (token-input (:token state))
       [:.overflow-x-auto.max-h-72
        (block-attrs-view state)]
       [:.w-full.text-sm.text-left.p-1.font-semibold.text-gray-900 "Token Metadata Insights"]
       (semantic-tabs state config/token-semantics)
       (case (-> state :option :semantics)
         :api  (api-view state)
         :word (word-view state)
         (website-view state))
       (template-view state)
       [:.card-actions.justify-center
        [:button.btn.btn-sm.btn-primary
         {:on-click #(handle-action state)} "Confirm"]
        [:button.btn.btn-sm.btn-ghost
         {:on-click #(js/logseq.hideMainUI)} "Cancel"]]]]]))

(comment
  (in-ns 'ui)
  plugin-state
  @plugin-state
  (-> @plugin-state :option :child-block-format)
  (js/logseq.showMainUI)
  (js/logseq.hideMainUI)
  (rum/mount (plugin-panel) (.getElementById js/document "app"))
  (swap! plugin-state assoc :slash-commands {:name "Bob" :gender :male})
  (do
    (ls/reload-plugin "logseq-url-plus")
    (js/console.clear))
  ;; djblue/portal experiments. 
  ;; Follow https://cljdoc.org/d/djblue/portal/0.35.1/doc/remote-api
  ;; to run portal UI hosting process
  ;; $ rlwrap bb -cp `clj -Spath -Sdeps '{:deps {djblue/portal {:mvn/version "0.35.1"}}}'`
  ;; user=> (require '[portal.api :as p])
  ;; user=> (p/open {:port 5678})
  (do
    (require '[portal.client.web :as p])
    (def submit (partial p/submit {:port 5678}))
    (add-tap #'submit))
  (tap> [:hello :world])
  (tap> @plugin-state)
  ;; portal.api requires jvm/node, not for browser runtime 
  ;; (require '[portal.api :as p])
  ;; (def p (p/open))
  ;; (def p (p/open {:launcher :vs-code}))
  ;; (add-tap #'p/submit)
  ;; Not what we want. JS runtime will be moved out of logseq to http://localhost:8080
  ;; (require 'shadow.remote.runtime.cljs.browser)
  )