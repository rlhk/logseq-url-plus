# URL+ Plugin for Logseq

URL+ is a [Logseq](https://logseq.com) plugin written in [ClojureScript](https://clojurescript.org) with [shadow-cljs](https://github.com/thheller/shadow-cljs) as the main tooling.

The plugin takes the last token of an editing block, be it a word or a URL, and augments the block with associated metadata in various formats.

If the last token is a valid URL, page metadata or an API response is fetched, then the block is rewritten by applying a selected template. If the last token is a word, a compact dictionary definition can be appended instead.

![demo](https://raw.githubusercontent.com/rlhk/logseq-url-plus/main/demo.gif)

## Installation

Install **URL+** from the Logseq marketplace: `Plugins` → `Marketplace` → search for *URL+*.

To run from source, see the [technical notes](./doc/dev-notes.md).

## Slash Commands

Each command can be enabled or disabled individually in the plugin settings.

- `URL+ [title](url)`
- `URL+ [title](url) description`
- `URL+ Metadata -> Logseq Attributes`
- `URL+ Metadata -> EDN Code`
- `URL+ Metadata -> JSON Code`
- `URL+ API -> Logseq Attributes`
- `URL+ API -> Logseq Attributes Block`
- `URL+ API -> EDN Code`
- `URL+ API -> JSON Code`
- `URL+ Append Word Definition`
  - Uses [dictionaryapi.dev](https://dictionaryapi.dev), a free community-run
    service with no uptime guarantee
- `URL+ Inspector ...`
  - Opens the inspector UI for token insights and template customization
  ![Inspector UI](https://raw.githubusercontent.com/rlhk/logseq-url-plus/main/inspector-ui.png)

## URL Handling

Before anything is fetched or written to your graph, the URL is tidied up:

- **Short and mobile YouTube links are canonicalized.** `youtu.be/<id>`,
  `/shorts/<id>`, `/live/<id>`, `/embed/<id>` and `m.youtube.com` all resolve to
  `youtube.com/watch?v=<id>`, so metadata can be read from them.
- **Tracking parameters are stripped** — `utm_*`, `fbclid`, `gclid`, `igshid`,
  YouTube's `si`, and similar. Parameters you meant to keep, such as a `t=`
  timestamp, and the `#fragment` are preserved.
- **Fetched titles and descriptions are escaped** so remote text cannot inject
  markdown links or Logseq property and macro syntax into your notes.

## Settings

Open `Plugins` → `URL+` → `Settings`.

| Setting | Effect |
| --- | --- |
| Attributes to **EXCLUDE** in URL metadata | Drop these keys from fetched metadata. Comma- or space-separated, case sensitive. |
| Attributes to **INCLUDE** in URL metadata | Keep only these keys. Applied after the exclude list. |
| One toggle per slash command | Register or hide that command. The Inspector has its own toggle. |

## Compatibility

URL+ targets **Logseq OG** — the file-based (Markdown) version. Logseq split
into two products in April 2026, and the database version has a different
plugin API surface; a port is not yet available.

## Changes in 0.2.0

- **Short URLs work again.** Links that redirect across hosts — `youtu.be`,
  `t.co`, `bit.ly` — previously failed outright. This was the main reason to
  cut this release.
- **Failures are now reported.** Network errors, non-JSON responses and unknown
  words used to fail silently; they now show a message instead of doing nothing.
- **Fewer network requests.** Commands that only need page metadata no longer
  also fetch the page a second time as an API call.
- **Removed: `URL+ Extract tweet text of twitter.com`.** Tweet lookup left the
  free Twitter API tier in February 2023, so the command could not work without
  a paid developer plan. The Twitter access token setting is gone with it.

## Why Another URL Formatter?

- Prefer slash command `/` over autoformat
- Need more formatting templates beyond the default `[title](url)`
- Works for block with multiple terms and even multiline. The plugin only considers the last term without other content in the active editing block
- Persist URL metadata or API response in graph
- Learn Logseq plugin dev with ClojureScript + Rum + Babashka + tailwindcss (Sample ClojureScript based plugin projects are rare when this project started)

## Plugin Development in ClojureScript

See [technical notes](./doc/dev-notes.md) and [AGENTS.md](./AGENTS.md).
