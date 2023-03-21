(ns config)

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
   {:desc "URL+ Append Word Definition"
    :type :api/define
    :block "%(but-last)s%(token)s #card"
    :child "%(definition)s"
    :setting-key "UrlPlusAppendDef"}
   {:desc "URL+ Extract tweet text of twitter.com"
    :type :api/tweet
    :block "%(but-last)s%(token)s #tweet"
    :child "%(tweet-text)s\n%(tweet-author)s (%(tweet-time)s)"
    :setting-key "UrlPlusExtractTweet"}
   #_{:desc "URL+ Link Wiktionary URL"
    :type :link/define
    :block "%(but-last)s[%(token)s](%(url)s)"}])

(defn- desc->settings-title [s]
  (str "Register '" s "' in global slash commands"))

(defn- slash-command->setting-entry [{:keys [desc setting-key]}]
  (let [description (desc->settings-title desc)]
    {:key setting-key
     :type "boolean"
     :title desc
     :description description
     :default true}))

(def ls-plugin-settings
  (let [twitter-settings [{:key "TwitterAccessToken"
                           :type "string"
                           :title "Twitter Access Token"
                           :description "See: https://developer.twitter.com/en/docs/authentication/oauth-2-0/bearer-tokens"
                           :default ""}]
        inspector-settings [{:key "UrlPlusInspector"
                             :type "boolean"
                             :title "URL+ Inspector ..."
                             :description (desc->settings-title "URL+ Inspector ...")
                             :default true}]
        slash-cmd-settings (mapv slash-command->setting-entry slash-commands)]
    (into [] (concat twitter-settings
                     inspector-settings
                     slash-cmd-settings))))

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
