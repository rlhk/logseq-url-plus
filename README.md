# URL+ Plugin for Logseq

URL+ is a [Logseq](https://logseq.com) plugin written in [ClojureScript](https://clojurescript.org) with [shadow-cljs](https://github.com/thheller/shadow-cljs) as the main tooling.

The plugin assumes the last term of the active Logseq block to be a valid URL. It then tries to retreive the metadata or API response from that URL; formats and rewrites the URL with various templates.

![demo](demo.gif)

## Slash Commands

- `URL+ [title](url)`
- `URL+ [title](url) description`
- `URL+ Metadata -> Logseq Attributes`
- `URL+ Metadata -> EDN Code`
- `URL+ Metadata -> JSON Code`
- `URL+ API -> Logseq Attributes`
- `URL+ API -> EDN Code`
- `URL+ API -> JSON Code`

## Why Another URL Formatter?

- Prefer slash command `/` over autoformat
- Need more formatting templates beyond the default `[title](url)`. Customizable templates in plan
- Works for block with multiple terms and even multiline block too. The plugin only considers the last term in the block
- Presist URL metadata or API response in graph
- Learn Logseq plugin dev in ClojureScript (most plugins today are written in JS/TS, if not all)

## Plugin Devevelopment in ClojureScript

See [dev-notes.md](./doc/dev-notes.md) for technical notes.

## Prior Arts and Reference Plugins
- https://github.com/logseq/logseq-plugin-samples (official sample)
- https://github.com/pengx17/logseq-plugin-link-preview
- https://github.com/0x7b1/logseq-plugin-automatic-url-title
- https://github.com/superman66/logseq-plugin-url-md (not recommended by author)
- https://github.com/trashhalo/logseq-dictionary
