(ns config
  "Static plugin data: slash commands, settings schema and initial UI state.")

(def dictionary-api-base
  "Base URL for the free dictionaryapi.dev lookup service.

  Community-run and unfunded, with no SLA - callers must surface failures
  rather than assume a definition comes back."
  "https://api.dictionaryapi.dev/api/v2/entries/en/")

(def token-semantics
  {:website "Website" :api "API" :word "Word"})

(def block-attrs [:token :token-label :url :block-content :block-content-before-token])

(def child-block-options
  {:json "JSON Code"
   :edn  "EDN Code"
   :logseq-attrs "Logseq Attributes"
   :table "Markdown Table"
   :definition "Word Definition"})

(def content-templates
  {:default "%(block-content)s"
   :before-title-url "%(block-content-before-token)s[%(title)s](%(url)s)"})

(def slash-commands
  [{:desc "URL+ [title](url)"
    :type :meta
    :mode :template ; Supported response types: (or :meta :api :api/define :link/define)
    ;; 2 modes are supported: 
    ;; :template (default, base on string template defined in :block & :child) 
    ;; :block (use :api-blocks attrs) as children blocks. Ignore :child template )
    :block "%(but-last)s[%(title)s](%(url)s)"
    :setting-key "UrlPlusTitle"}
   {:desc "URL+ [title](url) description"
    :type :meta
    :block "%(but-last)s[%(title)s](%(url)s) %(description)s"
    :setting-key "UrlPlusTitleDesc"}
   {:desc "URL+ Metadata -> Logseq Attributes"
    :type :meta
    :block "%(but-last)s%(link-or-url)s\n%(meta-attrs)s\n"
    :setting-key "UrlPlusMetaToAttrs"}
   {:desc "URL+ Metadata -> EDN Code"
    :type :meta
    :block "%(but-last)s%(token)s"
    :child "```edn\n%(meta-edn)s```"
    :setting-key "UrlPlusMetaToEDN"}
   {:desc "URL+ Metadata -> JSON Code"
    :type :meta
    :block "%(but-last)s%(token)s"
    :child "```json\n%(meta-json)s\n```"
    :setting-key "UrlPlusMetaToJSON"} ; :child is optional for mode :template 
   {:desc "URL+ API -> Logseq Attributes"
    :type :api
    :block "%(but-last)s%(link-or-url)s\n%(api-attrs)s\n"
    :setting-key "UrlPlusApiToAttrs"}
   {:desc "URL+ API -> Logseq Attributes Block"
    :type :api
    :mode :block
    :block "%(but-last)s%(token)s"
    :setting-key "UrlPlusApiToAttrsBlk"}
   {:desc "URL+ API -> EDN Code"
    :type :api
    :block "%(but-last)s%(token)s"
    :child "```edn\n%(api-edn)s```"
    :setting-key "UrlPlusApiToEdn"}
   {:desc "URL+ API -> JSON Code"
    :type :api
    :block "%(but-last)s%(token)s"
    :child "```json\n%(api-json)s\n```"
    :setting-key "UrlPlusApiToJson"}
   {:desc "URL+ All links in block"
    ;; No :block template - `core/handle-all-links!` rewrites many spans in one
    ;; block rather than substituting a single token, so it does not fit the
    ;; `%(but-last)s` shape every other command uses.
    :type :all-links
    :setting-key "UrlPlusAllLinks"}
   {:desc "URL+ Append Word Definition"
    :type :api/define
    :block "%(but-last)s%(token)s #card"
    :child "%(definition)s"
    :setting-key "UrlPlusAppendDef"}
   ;; The Twitter/X tweet-extraction command was removed in 0.2.0: tweet lookup
   ;; left the free API tier in Feb 2023, so the command could not work for
   ;; anyone without a paid developer plan.
   ])

(defn- desc->settings-title [s]
  (str "Register '" s "' in global slash commands"))

(defn- slash-cmd->setting [{:keys [desc setting-key]}]
  (let [description (desc->settings-title desc)]
    {:key setting-key
     :type "boolean"
     :title desc
     :description description
     :default true}))

(def ls-plugin-settings
  (concat [{:key "UrlPlusExcludeAttrs"
            :type "string"
            :title "Attributes to EXCLUDE in URL metadata."
            :description "List attributes to exclude, separated by comma or space. Case sensitive."
            :default ""}
           {:key "UrlPlusIncludeAttrs"
            :type "string"
            :title "Attributes to INCLUDE in URL metadata."
            :description "List attributes to include, separated by comma or space. Case sensitive."
            :default ""}
           {:key "UrlPlusInspector"
            :type "boolean"
            :title "URL+ Inspector ..."
            :description (desc->settings-title "URL+ Inspector ...")
            :default true}]
          (mapv slash-cmd->setting slash-commands)))

(def persistent-state-keys
  [:block-template :option])

(def initial-state
  {:token nil
   :token-label nil
   :url nil
   :block-content nil
   :block-content-before-token nil
   :block-template (:default content-templates)
   :option {:semantics :website}
   :block nil
   :child nil})

(defonce plugin-state (atom initial-state))