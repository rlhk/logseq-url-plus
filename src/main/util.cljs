(ns util
  "Utility helpers for data transformation, DOM etc..."
  (:require
   [medley.core :refer [filter-vals]]
   [cuerdas.core :as str]
   [goog.html.textExtractor :as gtext]))

(def ^:private named-entities
  {"&amp;" "&" "&lt;" "<" "&gt;" ">" "&quot;" "\"" "&apos;" "'"
   "&#39;" "'" "&nbsp;" " " "&mdash;" "\u2014" "&ndash;" "\u2013"
   "&hellip;" "\u2026" "&rsquo;" "\u2019" "&lsquo;" "\u2018"
   "&ldquo;" "\u201c" "&rdquo;" "\u201d"})

(defn- decode-entities
  "Decode HTML entities without a DOM. Handles the common named entities plus
  decimal and hex numeric references."
  [s]
  (-> (reduce-kv (fn [acc entity ch] (str/replace acc entity ch)) (str s) named-entities)
      (str/replace #"&#(\d+);"
                   (fn [[_ n]] (js/String.fromCodePoint (js/parseInt n 10))))
      (str/replace #"&#[xX]([0-9a-fA-F]+);"
                   (fn [[_ n]] (js/String.fromCodePoint (js/parseInt n 16))))))

;; https://github.com/google/closure-library/blob/master/closure/goog/html/textextractor.js#L13
(defn decode-html-content
  "Decode entities in remote text such as a fetched page title.

  Uses Closure's text extractor when a DOM is available - that is the case in
  the plugin, which runs inside a Logseq iframe, and it also strips any markup.
  Falls back to a plain entity decode when there is no `document`, so this
  namespace remains usable in a Node runtime; that is what lets the integration
  harness exercise the :meta pipeline."
  [s]
  (when (some? s)
    (if (exists? js/document)
      (gtext/extractTextContent s)
      (decode-entities s))))

(defn devlog [& msgs]
  (when goog.DEBUG
    (apply js/console.log (into ["URL+"] msgs))))

(defn target-value [e]
  (.. e -target -value))

(defn target-checked [e]
  (.. e -target -checked))

(defn ednize [data]
  (js->clj data :keywordize-keys true))

;; https://github.com/lambdaisland/uri maybe useful
(defn url? [s]
  (try
    (js/URL. s)
    true
    (catch js/Object _e false)))

(defn http? [s]
  (and (str/starts-with? s "http")
       (url? s)))

(def ^:private youtube-hosts
  "Hosts whose /shorts, /live, /embed and mobile forms resolve to a watch URL."
  #{"youtube.com" "www.youtube.com" "m.youtube.com" "music.youtube.com"
    "youtube-nocookie.com" "www.youtube-nocookie.com"})

(defn- youtube-video-id
  "Return the video id for a short-form YouTube URL object, else nil."
  [u]
  (let [host (str/lower (.-hostname u))
        path (.-pathname u)]
    (cond
      ;; https://youtu.be/<id>
      (= host "youtu.be")
      (let [seg (second (str/split path #"/"))]
        (when-not (str/blank? seg) seg))

      ;; https://www.youtube.com/{shorts,live,embed,v}/<id>
      (contains? youtube-hosts host)
      (second (re-find #"^/(?:shorts|live|embed|v)/([^/?#]+)" path))

      :else nil)))

(defn canonicalize-url
  "Normalize known URL shapes so metadata scrapers can read them.

  Today that means YouTube: `youtu.be/<id>`, `/shorts/<id>`, `/live/<id>`,
  `/embed/<id>` and the `m.` mobile host all become
  `https://www.youtube.com/watch?v=<id>`. Deliberate query parameters such as
  a `t=` timestamp are preserved; the `si=`/`feature=` share-tracking ones are
  not. Anything unrecognized or unparseable is returned unchanged."
  [url]
  (if-not (and (string? url) (url? url))
    url
    (let [u    (js/URL. url)
          host (str/lower (.-hostname u))]
      (if-let [id (youtube-video-id u)]
        (let [params (.-searchParams u)]
          (.delete params "si")
          (.delete params "feature")
          (.set params "v" id)
          (str "https://www.youtube.com/watch?" (.toString params)))
        (if (contains? youtube-hosts host)
          ;; Already a watch URL; just normalize the host and drop share noise.
          (do (.delete (.-searchParams u) "si")
              (set! (.-hostname u) "www.youtube.com")
              (.-href u))
          url)))))

(def ^:private md-link-re
  ;; Anchored and greedy so a URL containing parentheses is captured whole -
  ;; e.g. [Dog](https://en.wikipedia.org/wiki/Dog_(disambiguation)). The
  ;; previous unanchored, lazy pattern stopped at the first ")", truncating the
  ;; URL and leaving a stray bracket in the block. Anchoring is safe because
  ;; callers pass a single token from `else-and-last`, not a whole block.
  #"^\[(.*)\]\((.*)\)$")

(defn str->md-link
  "Parse a markdown link into {:label ... :link ...}, or nil if `s` is not one."
  [s]
  (when (string? s)
    (when-let [[_ label link] (re-find md-link-re (str/trim s))]
      {:label label :link link})))

(defn md-link->str [{:keys [label link]}]
  (str/format "[%s](%s)" label link))

(defn md-link? [s]
  (some? (:link (str->md-link s))))

(defn safe-fmt
  "Format `template` with `data`, returning a marker instead of throwing.

  Inspector templates are free text re-rendered on every keystroke, so a
  half-typed `%(...)s` or an unknown key must not blow up the Rum render."
  [template data]
  (try
    (str/fmt (or template "") data)
    (catch :default e
      (str "<template error: " (or (.-message e) e) ">"))))

(defn md-inline-escape
  "Make fetched remote text safe to embed in a Logseq block.

  Page titles and descriptions are written straight into the user's graph, so
  content that is merely awkward - let alone hostile - can break out of the
  markdown link it sits in or be parsed as Logseq syntax. This collapses
  newlines (which would otherwise split the block or corrupt `key:: value`
  attribute lines), escapes the square brackets that delimit a markdown link
  label, and defuses Logseq's property and macro markers.

  Returns nil for nil so callers can thread it safely."
  [s]
  (when (some? s)
    (-> (str s)
        (str/replace #"\s*\r?\n\s*" " ")
        (str/replace #"([\[\]])" "\\$1")
        (str/replace "::" ":\\:")
        (str/replace "{{" "{\\{")
        str/trim)))

(comment
  (str->md-link "[I'm label](I'm link)")
  (md-link->str {:label "I'm label" :link "Just a link"}))

(defn content-type
  "Parse an HTTP Content-Type header into {:mime-type ... :charset ...}.

  Tolerates a header with no parameter section (plain `application/json`),
  which is common and which the previous regex rejected outright, and
  lower-cases the mime type so comparisons are case-insensitive."
  [s]
  (when (and (string? s) (not (str/blank? s)))
    (let [[mime & params] (str/split (str/trim s) #";")
          kvs (into {}
                    (keep (fn [param]
                            (let [[k v] (str/split (str/trim param) #"=" 2)]
                              (when (and k v)
                                [(keyword (str/lower (str/trim k)))
                                 ;; Parameter values may be quoted.
                                 (str/replace (str/trim v) #"^\"|\"$" "")]))))
                    params)]
      (assoc kvs :mime-type (str/lower (str/trim mime))))))

(defn json-response?
  "True when a Content-Type header denotes JSON, including vendor suffixes
  such as `application/vnd.api+json` and the `text/json` variant."
  [content-type-str]
  (let [mime (:mime-type (content-type content-type-str))]
    (boolean
     (and mime
          (or (= mime "application/json")
              (= mime "text/json")
              (str/ends-with? mime "+json"))))))

(comment
  (content-type "application/json; charset=utf-8")
  (json-response? "application/json; charset=utf-8"))

(defn nested? [data]
  (cond
    (not (or (map? data) (sequential? data))) false
    (not (seq data)) false
    (or (map? data) (sequential? data)) true
    :else false))

(defn attrs-and-children
  "Split flat values and nested values of a map"
  [data]
  [(filter-vals #(not (nested? %)) data)
   (filter-vals #(nested? %) data)])

; https://stackoverflow.com/questions/15020669/clojure-multiline-regular-expression
(defn else-and-last [s]
  (->> (str/rtrim s)
       (re-find #"(?is)(.*?\s*)(\[.*?\]\(.*?\)|\S+?)$")
       rest))

;; ---------------------------------------------------------------- caret split
;;
;; `else-and-last` above is anchored at `$`, so it can only ever return the
;; final token. Everything below turns that into "the token at the cursor" by
;; cutting the string at a token boundary first and handing the head to it
;; unchanged - so the end-of-block case is byte-identical by construction.

(defn- span-matcher
  "A fresh global RegExp for `src`. Needed because `re-seq` discards offsets."
  [src]
  (js/RegExp. src "g"))

(def ^:private token-span-src
  ;; Markdown-link branch first, so `[a label with spaces](url)` is ONE token -
  ;; a whitespace split cannot express that. `[^\s]*` for the URL part rather
  ;; than `[^)]*` so `[Dog](.../Dog_(disambiguation))` survives; see the note on
  ;; `md-link-re` above for the bug that taught us this.
  "\\[[^\\]\\n]*\\]\\([^\\s]*\\)|\\S+")

(defn- token-end-at-caret
  "Snap `pos` rightwards to the end of the token enclosing it, else return it.

  A caret inside a URL means that whole URL, not the half to its left."
  [s pos]
  (let [re (span-matcher token-span-src)]
    (loop []
      (if-let [m (.exec re s)]
        (let [start (.-index m)
              end   (+ start (count (aget m 0)))]
          (if (and (< start pos) (< pos end)) end (recur)))
        pos))))

(defn split-at-caret
  "Split `content` around the caret at `pos` into `{:before :token :after}`.

  `:token` is the one the caret sits inside or immediately after; `:after` is
  everything past it. With the caret at the end of the block - or with `pos`
  unusable - this degrades to plain `else-and-last` with an empty `:after`,
  which is what keeps existing behaviour byte-identical rather than merely
  well-tested.

  No non-whitespace character is ever dropped. Whitespace at the join is
  normalised on purpose: Logseq only opens its command menu when `/` follows a
  space and leaves that space in the block, so carrying it over as well would
  widen the block by one space on every mid-block invocation."
  [content pos]
  (let [content (or content "")
        usable? (and (number? pos)
                     (not (js/isNaN pos))
                     (<= 0 pos (count content)))]
    (if-not usable?
      (let [[before token] (else-and-last content)]
        {:before before :token token :after ""})
      (let [end            (token-end-at-caret content pos)
            head           (subs content 0 end)
            tail           (subs content end)
            [before token] (else-and-last head)
            dropped        (subs head (count (str/rtrim head)))
            after          (if (and (seq dropped) (re-find #"^\s" tail))
                             tail
                             (str dropped tail))]
        {:before before
         :token  token
         :after  (if (str/blank? after) "" after)}))))

(defn splice-after-first-line
  "Insert `tail` at the end of the first line at or after index `from` in `s`.

  Eight of the block templates are a single line, where this is the same as
  appending. The two attribute templates are not: they emit `key:: value`
  lines, which have to own their lines, so a tail appended at the very end
  would land below them and corrupt the block."
  [s tail from]
  (if (str/blank? tail)
    s
    (let [from (min (max 0 (or from 0)) (count s))
          nl   (str/index-of s "\n" from)]
      (if nl
        (str (subs s 0 nl) tail (subs s nl))
        (str s tail)))))

(def ^:private md-link-span-src "\\[[^\\]\\n]*\\]\\([^\\s]*\\)")

(defn- trim-url-punctuation
  "Drop sentence punctuation a bare URL swept up from surrounding prose.

  A trailing `)` is only dropped when the URL has no `(` of its own, so
  `.../Dog_(disambiguation)` keeps its bracket while `(see https://a.com)`
  does not keep the closing one."
  [url]
  (let [url (str/replace url #"[.,;:!?]+$" "")]
    (if (and (str/ends-with? url ")") (not (str/includes? url "(")))
      (subs url 0 (dec (count url)))
      url)))

(defn url-spans
  "Every bare URL in `s` as `[start end url]`, left to right.

  URLs already inside a markdown link are skipped so they are not wrapped a
  second time."
  [s]
  (let [s     (or s "")
        links (let [re (span-matcher md-link-span-src)]
                (loop [acc []]
                  (if-let [m (.exec re s)]
                    (recur (conj acc [(.-index m) (+ (.-index m) (count (aget m 0)))]))
                    acc)))
        inside? (fn [i] (boolean (some (fn [[a b]] (and (>= i a) (< i b))) links)))
        re      (span-matcher "https?://\\S+")]
    (loop [acc []]
      (if-let [m (.exec re s)]
        (let [start (.-index m)
              url   (trim-url-punctuation (aget m 0))]
          (recur (if (or (inside? start) (str/blank? url))
                   acc
                   (conj acc [start (+ start (count url)) url]))))
        acc))))

(defn to-fixed [number places]
  (.toFixed number places))

(defn records? [data]
  (and (sequential? data), (map? (first data))))

(defn tokenize-str
  "Parse a string of words into a vector of tokens by both comma and whitepace."
  [s]
  (-> (str/trim s)
      (str/replace #"," " ")
      (str/split #"\s+")))

(defn exclude-include-ks
  "Exclude then include keys from a map."
  [m exclude-keys include-keys]
  (cond-> m
    (seq exclude-keys) (select-keys (remove (set exclude-keys) (keys m)))
    (seq include-keys) (select-keys include-keys)))

(def tracker-prefixes
  "Query parameter name prefixes treated as tracking noise."
  ["utm_"])

(def tracker-names
  "Exact query parameter names treated as tracking noise."
  #{"fbclid" "gclid" "dclid" "gbraid" "wbraid" "msclkid" "mkt_tok"
    "igshid" "igsh" "mc_cid" "mc_eid" "ref_src" "ref_url"
    "si" "spm" "scm" "yclid" "_ga" "_gl" "vero_id" "twclid"})

(defn- tracker-param?
  [param-name prefixes names]
  (let [k (str/lower param-name)]
    (or (contains? names k)
        (boolean (some #(str/starts-with? k (str/lower %)) prefixes)))))

(defn remove-url-trackers
  "Strip tracking query parameters from a URL string.

  Fragment-aware: the previous version split on `?` only, so a URL such as
  `https://x.com/p?utm_source=a#section` silently lost `#section` along with
  the parameter. Matching is case-insensitive and covers common non-`utm_`
  trackers (fbclid, gclid, igshid, YouTube's si, ...).

  Kept parameters are passed through byte-for-byte rather than re-encoded, so
  percent-encoded values and the base URL survive untouched."
  ([url] (remove-url-trackers url tracker-prefixes tracker-names))
  ([url prefixes] (remove-url-trackers url prefixes tracker-names))
  ([url prefixes names]
   (if-not (string? url)
     url
     (let [[before-fragment fragment] (str/split url #"#" 2)
           [base query]               (str/split before-fragment #"\?" 2)
           suffix                     (if fragment (str "#" fragment) "")]
       (if (str/blank? query)
         (str base suffix)
         (let [kept (->> (str/split query #"&")
                         (remove (fn [param]
                                   (-> (str/split param #"=" 2)
                                       first
                                       (tracker-param? prefixes names))))
                         (str/join "&"))]
           (if (str/blank? kept)
             (str base suffix)
             (str base "?" kept suffix))))))))