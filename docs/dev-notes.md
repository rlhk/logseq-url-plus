# Developing this plugin

A Logseq plugin in ClojureScript, built with shadow-cljs, Rum and Tailwind +
daisyUI, driven by [babashka](https://babashka.org) tasks.

Targets **Logseq OG** (file-based graphs) on macOS. Everything below was
verified against Logseq 0.10.15.

- [Official plugin samples](https://github.com/logseq/logseq-plugin-samples)
- [`@logseq/libs` API docs](https://logseq.github.io/plugins/)

## Prerequisites

```
bb doctor
```

Reports every missing tool and the command to install it. It checks babashka,
a JDK, node, yarn, clj-kondo and `node_modules`.

**shadow-cljs needs JDK 21+** and dies with `UnsupportedClassVersionError` on
anything older — but you do not need to export `JAVA_HOME`. The tasks find a
JDK themselves (`JAVA_HOME`, `/Library/Java/JavaVirtualMachines`, Homebrew,
SDKMAN) and take the *lowest* qualifying one, matching the version CI pins. A
system default of JDK 17 is fine; they look past it. `bb java` shows the
choice. On JDK 24+ they add `--sun-misc-unsafe-memory-access=allow` to silence
a protobuf warning — hence `NOTE: Picked up JDK_JAVA_OPTIONS`.

## Quick start

```
yarn install          # once, after cloning
bb dev                # watch CLJS + Tailwind, re-run tests on save (blocks)
bb sideload           # register dist/ with a running Logseq as a dev plugin
```

Then in Logseq: enable developer mode, and confirm **URL+ (dev)** is listed
under Plugins. After that, every code change is:

```
# save a file -> the watch rebuilds dist/ (~10s)
bb reload             # push it into the running Logseq
```

`bb sideload` needs Logseq launched with a debugging port — see
[Driving Logseq over CDP](#driving-logseq-over-cdp). Without one, load the
plugin by hand: Plugins -> Load unpacked plugin -> the project root (the folder
with `package.json` *and* `dist/`; Logseq requires the entry `package.json`
even in dev mode). Console: `Option Command + i`.

## Tasks

`bb tasks` lists them all. The ones used most:

| Task | Purpose |
| --- | --- |
| `bb doctor` | Check the toolchain and report what is missing |
| `bb dev` | Watch CLJS + Tailwind, re-running tests on save (blocks) |
| `bb dev-start` | The same watch, backgrounded, waits until ready |
| `bb dev-logs` | Show the background watch log |
| `bb stop` / `bb restart` | Stop or replace a running watch |
| `bb sideload` | Create/refresh the dev plugin copy and register it |
| `bb reload` | Reload the side-loaded plugin in a running Logseq |
| `bb repl-status` | Is the `:plugin` CLJS runtime attached? |
| `bb test` | Unit + integration suites |
| `bb lint` | clj-kondo over `src` — keep at zero warnings |
| `bb check-css` | Verify every daisyUI class used by the UI still exists |
| `bb build` | Release bundle into `dist/` |
| `bb ci` | Everything CI runs: lint, check-css, test, build |
| `bb deps` | Check for dependency updates (exit 1 = updates available) |
| `bb release` | Run CI, then tag and push |

The tasks serve coding agents as well as people: non-interactive, safe to
re-run, signalling state through exit codes rather than only prose, and
refusing known footguns (a second watch, or a build while one is running)
rather than documenting them. `bb dev-start` exists because `bb dev` never
returns — it would hang an agent's tool call.

While a watch is up: `dist/` is served on <http://localhost:8080>, the
shadow-cljs dashboard on <http://localhost:9630>, and nREPL on port **8702**.

## The dev plugin copy

`bb sideload` builds a second plugin directory — `~/logseq-url-plus-dev` by
default, override with `DEV_PLUGIN_DIR` — containing:

- `package.json` derived from this project's, with `logseq.id` changed to
  `logseq-url-plus-dev`
- `dist` as a **symlink** to this project's `dist/`, so the watch's output is
  picked up with no second sync step

The distinct id is load-bearing. Logseq will not run two plugins with the same
id side by side and rejects a colliding registration, so without it you would
have to disable the Marketplace copy first. Settings are keyed by id too
(`~/.logseq/settings/<id>.json`), so it also keeps dev settings out of the
installed plugin's.

The task is idempotent: re-run it to refresh `package.json` after a version
bump; it reloads rather than re-registering if the plugin is already there.

## REPL

### Check readiness first

The plugin's CLJS runtime lives **inside a Logseq iframe**, so it does not
exist until Logseq is running with the plugin loaded. Three states are easy to
conflate:

1. **watch alive** — a shadow-cljs worker exists for the build
2. **build ready** — that worker compiled successfully
3. **runtime attached** — a live JS runtime is connected

```
$ bb repl-status
server=running
:plugin watch=running runtimes=1
:test watch=running runtimes=0
ready=yes
```

If `:plugin runtimes=0`, do **not** attach or evaluate yet — you would be
talking to the JVM Clojure REPL instead of the plugin, and the results are
baffling. Start the watch and load the plugin in Logseq first.

(`:test runtimes=0` is normal. The `:node-test` build spawns a runtime per run
and exits.)

### Calva

Connect Calva to the shadow-cljs nREPL on port 8702, build `:plugin`.

![](./imgs/calva-repl-1.png)
![](./imgs/calva-repl-2.png)
![](./imgs/calva-repl-3.png)
![](./imgs/calva-repl-4.png)

Smoke-test with:

```clojure
(in-ns 'core)
config/slash-commands          ; the registered commands
(js/alert "Hello")
(js/console.log "Hello Console")
(ls/show-msg "Hello Logseq")   ; show-msg lives in ns `ls`, not `core`
```

![](./imgs/calva-repl-5.png)
![](./imgs/calva-repl-6.png)
![](./imgs/calva-repl-7.png)

The rich comment block at the end of `ui.cljs` has expressions for remounting
the panel and inspecting `@plugin-state`. Evaluate with `option + enter`.

Editor setup: [VS Code](https://code.visualstudio.com) +
[Calva](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva)
([Paredit](https://calva.io/paredit/)), optionally
[VSCode Neovim](https://marketplace.visualstudio.com/items?itemName=asvetliakov.vscode-neovim).

## Constraints and gotchas

Each of these cost real debugging time. They are constraints, not preferences.

### shadow-cljs is pinned to 3.1.2 and must not be upgraded while rum is 0.12.11

shadow-cljs 3.5.2 breaks `rum/defc` argument passing: every component that takes
arguments receives the raw `arguments` object instead. The Inspector renders
with `[object Arguments]` in the token field, empty attribute tables, no tabs,
and every `case` on a passed-in keyword falling through to its default branch.

Nothing fails at compile time and **no test catches it** — neither tier renders
a component. rum 0.12.11 is the latest release and upstream has been dormant
since July 2023, so there is no rum-side fix. Confirmed by bisect: identical
source and state renders correctly under 3.1.2, incorrectly under 3.5.2.

### Hot reload does not reach the plugin

`:after-load entry/reload` is wired and the runtime does attach, but a changed
bundle does not update the running plugin — verified by editing a visible string
and watching it reach `dist/index.js` and not Logseq. Use `bb reload` after
every change.

### `bb build` and `bb dev` both own `dist/`

`prep` deletes `dist/`, which a running watch is actively writing into. Doing
that mid-watch leaves whatever Logseq side-loaded a mix of two builds. `prep`
refuses when a watch is running; `ALLOW_BUILD_WITH_WATCH=1` overrides it. The
watch rebuilds `dist/` on every save, so a separate build is only needed to
verify the release bundle.

### Tailwind's `--watch` quits when stdin is not a TTY

So the tasks use `--watch=always`. With plain `--watch`, Tailwind treats stdin
closing as a shutdown signal and exits instantly whenever backgrounded, piped,
run under CI, or driven by an agent — producing **no `dist/styles.css` at all**,
silently, with exit status 0. It works in an interactive terminal, which is what
makes it easy to miss: the plugin loads completely unstyled and nothing says
why. Hence `bb dev-start` waits for `dist/styles.css`, not just for both builds
to report `Build completed`.

### `Browserslist: caniuse-lite is outdated` cannot be fixed here

Tailwind 3.4.0 bundles browserslist and caniuse-lite inside
`node_modules/tailwindcss/peers/index.js`, so the data is frozen in the released
package. `npx update-browserslist-db` cannot reach it, and since caniuse-lite is
not a direct dependency, running it removes packages without silencing anything.
`bb deps` deliberately does not call it. Cosmetic; goes away with Tailwind v4.

### Stale settings keys

`~/.logseq/settings/logseq-url-plus.json` keeps keys from removed features
(`TwitterAccessToken`, `UrlPlusExtractTweet`). Harmless — they are simply no
longer in the settings schema.

## Testing

`bb test` runs both tiers (shadow-cljs `:node-test`, `:autorun true`):

- **Unit** — pure functions in `src/test/*_spec.cljs`.
- **Integration** — `integration_spec.cljs` drives the real
  `core/handle-slash-cmd` against the fakes in `harness.cljs`: a `js/logseq`
  stub recording every Editor/UI call, and a fixture-backed `js/fetch`.
  Assertions are made on the exact block content that would be written.

Neither tier loads the built bundle into Logseq, so neither can catch a release
that fails on load — the `:advanced` build munges every name, and `ls.cljs`
reaches Logseq's API by property access that survives only because
`:infer-externs :auto` preserves it. Nor can either catch a rendering bug; see
the shadow-cljs pin above. **The manual checklist is the only coverage for
both.**

A third tier — true end-to-end against a running Logseq — is not implemented.
Logseq's own suite ([`clj-e2e`](https://github.com/logseq/logseq/tree/master/clj-e2e))
uses Wally over Playwright Java driven by babashka, which would fit this repo,
but there is no published way to load an *unpacked* plugin under automation.
Settling that needs a timeboxed spike: confirm whether plugins load in the
HTTP-served app, or drive the desktop binary with Playwright's
`_electron.launch` and side-load via `LSPluginCore.register(...)`.

### Manual smoke checklist

Run before tagging a release. Start the watch, `bb sideload`, then in a scratch
block:

1. `https://youtu.be/dQw4w9WgXcQ` + `/URL+ [title](url)` -> resolves to the real
   video title. This is the regression 0.2.0 exists to fix.
2. A `bit.ly` or `t.co` link -> resolves rather than throwing.
3. `https://jsonplaceholder.typicode.com/posts/1` + `/URL+ API -> JSON Code`
   -> JSON block. Confirm in DevTools that a metadata command issues **one**
   request, not two.
4. An unreachable host -> a visible message, no unhandled rejection in console.
5. `/URL+ Append Word Definition` on `prodigy` -> formatted definition; on a
   nonsense word -> "no definition found". (dictionaryapi.dev is an unfunded
   community service; a 5xx shows as "dictionary service unavailable" and is
   not a plugin bug.)
6. `/URL+ Inspector ...` -> modal opens, all three tabs render, Esc and
   backdrop-click close it, Confirm writes the block.
7. Empty block + any command -> graceful message, no throw.
8. `bb reload` -> each slash command appears **once** (11 total).

## Driving Logseq over CDP

Logseq is Electron, so its renderer speaks the Chrome DevTools Protocol.
Launched with a debugging port, `script/logseq-cdp.mjs` can evaluate JavaScript
in the app, read blocks back and take screenshots — no clicking required. This
is what `bb sideload` and `bb reload` use, and how the 0.2.0 bundle was verified
against a real Logseq.

```
# Quit Logseq first, then:
#
# Note the env -u: an integrated terminal (VS Code, Cursor) exports
# ELECTRON_RUN_AS_NODE=1, which makes the Electron binary run as plain Node and
# reject the Chromium flag with "bad option: --remote-debugging-port".
env -u ELECTRON_RUN_AS_NODE -u ELECTRON_NO_ATTACH_CONSOLE \
  /Applications/Logseq.app/Contents/MacOS/Logseq --remote-debugging-port=9223 &

node script/logseq-cdp.mjs targets
node script/logseq-cdp.mjs eval "LSPluginCore.registeredPlugins.get('logseq-url-plus-dev').options.version"
node script/logseq-cdp.mjs screenshot /tmp/logseq.png
```

Override the port with `LOGSEQ_CDP_PORT` (default 9223).

Two cautions learned the hard way:

- **`LSPluginCore.unregister` deletes the plugin folder** for anything installed
  under `~/.logseq/plugins`: `unload(true)` emits `unlink-plugin` when
  `isInstalledInDotRoot`. Never call it on a Marketplace install. `disable` is
  safe and reversible; `unregister` is not. `bb sideload` keeps the dev copy
  outside `~/.logseq/plugins` for exactly this reason.
- `register` persists the path into `~/.logseq/preferences.json` under
  `externals`. Snapshot that file before testing and restore it after.

Slash-command handlers are wired by the SDK as events *inside* the plugin
sandbox (`Editor["on" + hookName]`), so they cannot be fired from the host with
`caller.call` or `callUserModel`. Triggering a command still needs real keyboard
input — hence the manual checklist.

## Release

- Update `version` in `package.json`.
- `bb release` — runs CI, then tags that version and pushes. The tag triggers
  the GitHub workflow that builds the release the Marketplace picks up.
- Confirm the zip contains `dist/`, `package.json` and `README.md` only.

Note `@logseq/libs` is imported in exactly one place: `entry.cljs`, the build's
`:init-fn`. Importing it installs the `logseq` global as a side effect and needs
browser globals, so keeping it out of `core` and `ls` is what lets those
namespaces load under Node for the integration tier.

### Marketplace submission

Already listed, so this is only for reference:
[Marketplace README](https://github.com/logseq/marketplace/blob/master/README.md)
-> fork [logseq/marketplace](https://github.com/logseq/marketplace) -> update
`packages/logseq-url-plus` -> PR. The existing
[rlhk/marketplace](https://github.com/rlhk/marketplace) fork dates from 2023 and
must be synced with upstream before any new PR.

## Dependencies

`bb deps` checks both Node (npm-check-updates) and Clojure/Script
([antq](https://github.com/liquidz/antq)) and exits 1 when updates exist. It
pulls antq in with `-Sdeps`, so it needs no `~/.clojure/deps.edn` alias. Update
Clojure deps in `shadow-cljs.edn`, Node deps in `package.json` — but read the
shadow-cljs pin above first.

## Reference repositories

- [logseq/logseq-plugin-samples](https://github.com/logseq/logseq-plugin-samples) — official
- [pengx17/logseq-plugin-link-preview](https://github.com/pengx17/logseq-plugin-link-preview)
- [trashhalo/logseq-dictionary](https://github.com/trashhalo/logseq-dictionary) — dormant since 2023
- [kurtharriger/logseq-things3-plugin](https://github.com/kurtharriger/logseq-things3-plugin) — ClojureScript, not in the marketplace; dormant since 2022
- [0x7b1/logseq-plugin-automatic-url-title](https://github.com/0x7b1/logseq-plugin-automatic-url-title) — **archived**
