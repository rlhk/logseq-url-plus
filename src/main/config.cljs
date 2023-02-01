(ns config)

(defonce plugin-state
  (atom
   {;:slash-commands slash-commands
    ;:ls-plugin-settings ls-plugin-settings
    :token ""
    :token-semantics :website}))

(def token-semantics
  {:website "Website" :api-endpoint "API" :word "Word"})

(def metadata-formats
  {:json "JSON"
   :edn "EDN"
   :table "Table"
   :logseq-attr "Logseq Attributes"})

(def content-templates
  {:before-title-url "%(block-content-before-token)s[%(title)s](%(url)s)"})

(def ls-plugin-settings
  [{:key "TwitterAccessToken"
    :type "string"
    :title "Twitter Access Token"
    :description "See: https://developer.twitter.com/en/docs/authentication/oauth-2-0/bearer-tokens"
    :default ""}])

(def slash-commands
  [{:desc "URL+ [title](url)"
    :type :meta ; Supported response types: (or :meta :api :api/define :link/define)
    ;; 2 modes are supported: 
    ;; :template (default, base on string template defined in :block & :child) 
    ;; :block (use :api-blocks attrs) as children blocks. Ignore :child template )
    :mode :template
    :block "%(but-last)s[%(title)s](%(url)s)"}
   #_{:desc "URL+ Advanced ..."
    :op :advanced} ;; defaults to :default
   {:desc "URL+ [title](url) description"
    :type :meta
    :block "%(but-last)s[%(title)s](%(url)s) %(description)s"}
   {:desc "URL+ Metadata -> Logseq Attributes"
    :type :meta
    :block "%(but-last)s%(link-or-url)s\n%(meta-attrs)s\n"}
   {:desc "URL+ Metadata -> EDN Code"
    :type :meta
    :block "%(but-last)s%(token)s"
    :child "```edn\n%(meta-edn)s```"}
   {:desc "URL+ Metadata -> JSON Code"
    :type :meta
    :block "%(but-last)s%(token)s"
    :child "```json\n%(meta-json)s\n```"} ; :child is optional for mode :template 
   {:desc "URL+ API -> Logseq Attributes"
    :type :api
    :block "%(but-last)s%(link-or-url)s\n%(api-attrs)s\n"}
   {:desc "URL+ API -> Logseq Attribute Blocks"
    :type :api
    :mode :block
    :block "%(but-last)s%(token)s"}
   {:desc "URL+ API -> EDN Code"
    :type :api
    :block "%(but-last)s%(token)s"
    :child "```edn\n%(api-edn)s```"}
   {:desc "URL+ API -> JSON Code"
    :type :api
    :block "%(but-last)s%(token)s"
    :child "```json\n%(api-json)s\n```"}
   {:desc "URL+ Append Word Definition"
    :type :api/define
    :block "%(but-last)s%(token)s #card"
    :child "%(definition)s"}
   {:desc "URL+ Extract tweet text of twitter.com"
    :type :api/tweet
    :block "%(but-last)s%(token)s #tweet"
    :child "%(tweet-text)s\n%(tweet-author)s (%(tweet-time)s)"}
   #_{:desc "URL+ Link Wiktionary URL"
    :type :link/define
    :block "%(but-last)s[%(token)s](%(url)s)"}])