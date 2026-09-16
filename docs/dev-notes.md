# Developing this plugin

A Logseq plugin in ClojureScript, built with shadow-cljs, Rum and Tailwind +
daisyUI, driven by [babashka](https://babashka.org) tasks.

Targets **Logseq OG** (file-based graphs) on macOS. Everything below was
verified against Logseq 0.10.15.

- [Official plugin samples](https://github.com/logseq/logseq-plugin-samples)
- [`@logseq/libs` API docs](https://logseq.github.io/plugins/)

This doc is in three parts: **[Part 1](#part-1--common)** is the setup and the
facts that bind everyone; **[Part 2](#part-2--coding-agents)** and
**[Part 3](#part-3--human-coders)** cover what differs. Read Part 1 either way —
almost everything that has cost time here lives in it.

---

## Part 1 — Common

### Prerequisites

```
./script/bootstrap.sh
```

The one command that works on a machine with nothing on it. Plain POSIX `sh`,
no dependencies. It installs nothing; it tells you what is missing and hands
off to `bb doctor`, which reports every other tool — JDK, node, yarn,
clj-kondo, `node_modules` — and the command to fix each. Exit 0 means the
toolchain is complete.

Once babashka is present, `bb doctor` is the check to re-run; `bootstrap.sh`
only exists because `bb doctor` cannot report that `bb` itself is missing.
(A Makefile would not serve here: on macOS `/usr/bin/make` is a Command Line
Tools shim that fails until Xcode CLT is installed, so `make` is *less*
available than `/bin/sh`, not more.)

**shadow-cljs needs JDK 21+** and dies with `UnsupportedClassVersionError` on
anything older — but you do not need to export `JAVA_HOME`. The tasks find a
JDK themselves (`JAVA_HOME`, `/Library/Java/JavaVirtualMachines`, Homebrew,
SDKMAN) and take the *lowest* qualifying one, matching the version CI pins
(Java 21, Node 22). A system default of JDK 17 is fine; they look past it.
`bb java` shows the choice. On JDK 24+ they add
`--sun-misc-unsafe-memory-access=allow` to silence a protobuf warning — hence
`NOTE: Picked up JDK_JAVA_OPTIONS`.

### The edit → reload loop

**Hot reload does not work here.** `:after-load entry/reload` is wired and the
CLJS runtime does attach, but a changed bundle does not update the running
plugin — verified by editing a visible string and watching it reach
`dist/index.js` and never Logseq. `bb reload` is the substitute, and it is fast
enough that the loop still feels interactive.

Set up once:

```
yarn install
```

Quit Logseq, then relaunch it with a debugging port. Logseq is Electron, so its
renderer speaks the Chrome DevTools Protocol; this is what lets `bb sideload`
and `bb reload` work without clicking:

```
# The env -u matters: an integrated terminal (VS Code, Cursor) exports
# ELECTRON_RUN_AS_NODE=1, which makes the Electron binary run as plain Node and
# reject the Chromium flag with "bad option: --remote-debugging-port".
env -u ELECTRON_RUN_AS_NODE -u ELECTRON_NO_ATTACH_CONSOLE \
  /Applications/Logseq.app/Contents/MacOS/Logseq --remote-debugging-port=9223 &
```

Then start the watch and register the plugin:

```
bb dev            # blocks; agents want `bb dev-start` instead
bb sideload       # register dist/ with the running Logseq
```

Enable developer mode in Logseq and confirm **URL+ (dev)** is listed under
Plugins. From here every change is:

```
# save a file -> the watch rebuilds dist/ (~10s)
bb reload         # push it into the running Logseq
```

`bb reload` prints the slash-command count, which doubles as a smoke test:
anything other than 11 means registration is broken.

Without a debugging port, load the plugin by hand instead: Plugins -> Load
unpacked plugin -> the project root (the folder with `package.json` *and*
`dist/`; Logseq requires the entry `package.json` even in dev mode). You then
have to reload from the plugin panel by hand too. Console: `Option Command + i`.

### The dev plugin copy

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

### Tasks

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

The tasks refuse known footguns rather than documenting them — a second watch,
or a build while one is running — and signal state through exit codes, not only
prose.

While a watch is up: `dist/` is served on <http://localhost:8080>, the
shadow-cljs dashboard on <http://localhost:9630>, and nREPL on port **8702**.

### Constraints and gotchas

Each of these cost real debugging time. They are constraints, not preferences.

#### shadow-cljs is pinned to 3.1.2 and must not be upgraded while rum is 0.12.11

shadow-cljs 3.5.2 breaks `rum/defc` argument passing: every component that takes
arguments receives the raw `arguments` object instead. The Inspector renders
with `[object Arguments]` in the token field, empty attribute tables, no tabs,
and every `case` on a passed-in keyword falling through to its default branch.

Nothing fails at compile time and **no test catches it** — neither tier renders
a component. rum 0.12.11 is the latest release and upstream has been dormant
since July 2023, so there is no rum-side fix. Confirmed by bisect: identical
source and state renders correctly under 3.1.2, incorrectly under 3.5.2.

#### `bb build` and `bb dev` both own `dist/`

`prep` deletes `dist/`, which a running watch is actively writing into. Doing
that mid-watch leaves whatever Logseq side-loaded a mix of two builds. `prep`
refuses when a watch is running; `ALLOW_BUILD_WITH_WATCH=1` overrides it. The
watch rebuilds `dist/` on every save, so a separate build is only needed to
verify the release bundle.

#### Tailwind's `--watch` quits when stdin is not a TTY

So the tasks use `--watch=always`. With plain `--watch`, Tailwind treats stdin
closing as a shutdown signal and exits instantly whenever backgrounded, piped,
run under CI, or driven by an agent — producing **no `dist/styles.css` at all**,
silently, with exit status 0. It works in an interactive terminal, which is what
makes it easy to miss: the plugin loads completely unstyled and nothing says
why. Hence `bb dev-start` waits for `dist/styles.css`, not just for both builds
to report `Build completed`.

#### `Browserslist: caniuse-lite is outdated` cannot be fixed here

Tailwind 3.4.0 bundles browserslist and caniuse-lite inside
`node_modules/tailwindcss/peers/index.js`, so the data is frozen in the released
package. `npx update-browserslist-db` cannot reach it, and since caniuse-lite is
not a direct dependency, running it removes packages without silencing anything.
`bb deps` deliberately does not call it. Cosmetic; goes away with Tailwind v4.

#### Stale settings keys

`~/.logseq/settings/logseq-url-plus.json` keeps keys from removed features
(`TwitterAccessToken`, `UrlPlusExtractTweet`). Harmless — they are simply no
longer in the settings schema.

### Testing

`bb test` runs both tiers (shadow-cljs `:node-test`, `:autorun true`):

- **Unit** — pure functions in `src/test/*_spec.cljs`.
- **Integration** — `integration_spec.cljs` drives the real
  `core/handle-slash-cmd` against the fakes in `harness.cljs`: a `js/logseq`
  stub recording every Editor/UI call, and a fixture-backed `js/fetch`.
  Assertions are made on the exact block content that would be written.

Two things neither tier can catch, which is why the
[manual checklist](#manual-smoke-checklist) is not optional:

1. **A release that fails on load.** The `:advanced` build munges every name,
   and `ls.cljs` reaches Logseq's API by property access that survives only
   because `:infer-externs :auto` preserves it. Neither tier loads the built
   bundle into Logseq.
2. **A rendering bug.** Neither tier renders a component — see the shadow-cljs
   pin above, where the entire Inspector was broken with all 28 tests green.

A third tier — true end-to-end against a running Logseq — is not implemented.
Logseq's own suite ([`clj-e2e`](https://github.com/logseq/logseq/tree/master/clj-e2e))
uses Wally over Playwright Java driven by babashka, which would fit this repo,
but there is no published way to load an *unpacked* plugin under automation.
Settling that needs a timeboxed spike: confirm whether plugins load in the
HTTP-served app, or drive the desktop binary with Playwright's
`_electron.launch` and side-load via `LSPluginCore.register(...)`.

### Release

- Update `version` in `package.json`.
- `bb release` — runs CI, then tags that version and pushes. The tag triggers
  the GitHub workflow that builds the release the Marketplace picks up.
- Confirm the zip contains `dist/`, `package.json` and `README.md` only.

Note `@logseq/libs` is imported in exactly one place: `entry.cljs`, the build's
`:init-fn`. Importing it installs the `logseq` global as a side effect and needs
browser globals, so keeping it out of `core` and `ls` is what lets those
namespaces load under Node for the integration tier.

Already listed on the Marketplace, so submission is reference only:
[Marketplace README](https://github.com/logseq/marketplace/blob/master/README.md)
-> fork [logseq/marketplace](https://github.com/logseq/marketplace) -> update
`packages/logseq-url-plus` -> PR. The existing
[rlhk/marketplace](https://github.com/rlhk/marketplace) fork dates from 2023 and
must be synced with upstream before any new PR.

### Dependencies

`bb deps` checks both Node (npm-check-updates) and Clojure/Script
([antq](https://github.com/liquidz/antq)) and exits 1 when updates exist. It
pulls antq in with `-Sdeps`, so it needs no `~/.clojure/deps.edn` alias. Update
Clojure deps in `shadow-cljs.edn`, Node deps in `package.json` — but read the
shadow-cljs pin above first.

---

## Part 2 — Coding agents

**`AGENTS.md` at the repo root is the canonical agent contract** — exit codes
per task, coding conventions, and the interop rules in `ls.cljs` that
`:advanced` compilation depends on. Read it first; this section only covers
what is specific to driving the app.

### Use `bb dev-start`, never `bb dev`

`bb dev` is a watch that never returns and will hang the tool call.
`bb dev-start` runs the same watch detached, waits until both builds report
ready *and* `dist/styles.css` exists, prints a status block and exits 0 —
usually in under ten seconds. `bb dev-logs` shows its output.

### Check REPL readiness before evaluating

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
baffling. This is the single most common cause of "the REPL is behaving
strangely" here. Start the watch and load the plugin first.

(`:test runtimes=0` is normal. The `:node-test` build spawns a runtime per run
and exits.)

### Driving Logseq over CDP

`script/logseq-cdp.mjs` evaluates JavaScript in the running app, reads blocks
back and takes screenshots. `bb sideload` and `bb reload` are built on it, and
it is how the 0.2.0 bundle was verified against a real Logseq. Launch Logseq
with the debugging port as shown in [Part 1](#the-edit--reload-loop), then:

```
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

### Agent skills worth borrowing from

None of these are wired into this repo — it has no MCP server and no external
skill dependency, deliberately, so a checkout works with `bb` alone. They are
listed because the ideas in them are good and the gaps are worth knowing before
you reach for one.

- **[logseq/logseq `.agents/skills`](https://github.com/logseq/logseq/tree/master/.agents/skills)**
  — the most relevant reference available: same language, same build tool, same
  host app, and no MCP server. The watch-alive / build-ready / runtime-attached
  split above is taken from their
  [`logseq-repl`](https://github.com/logseq/logseq/tree/master/.agents/skills/logseq-repl)
  skill. Their `logseq-dependency-upgrade` skill is also where "verify usage
  before updating; remove unused packages instead of upgrading" comes from.
- **[humorless/clj-native-agent](https://github.com/humorless/clj-native-agent)**
  (formerly `clojure-dev-skill`) — five skills built on babashka and rewrite-clj
  that redirect an agent from text matching to structural edits and from
  `println` to REPL inspection ([background post](https://humorless.github.io/posts-output/agent-skill)).
  **Caveat:** every skill drives `brepl`, and the README does not mention
  ClojureScript or shadow-cljs at all. This project's REPL is a shadow-cljs
  nREPL whose runtime lives in a Logseq iframe, so the skills do not drop in
  unmodified. The two habits worth taking regardless — never hand-balance
  parens, lint after every save — are already in `AGENTS.md`.
- **[bhauman/clojure-mcp](https://github.com/bhauman/clojure-mcp)** — edits
  forms by type and name instead of by text match, validating delimiters before
  writing. Explicitly shadow-cljs aware: `list_nrepl_ports` discovers Clojure
  and shadow-cljs REPLs together.
- **[Calva Backseat Driver](https://github.com/BetterThanTomorrow/calva-backseat-driver)**
  — an MCP server inside Calva that reuses Calva's *existing* REPL connection,
  shadow-cljs builds and runtimes included. The lower-friction option if paren
  damage becomes a real problem, since the REPL workflow here is already Calva.

### Slash-command caret semantics

Measured in Logseq 0.10.15 with a temporary probe in `editing-context`, driven
by synthetic keystrokes over CDP. **A slash-command handler can trust the
caret**, which is what makes cursor-aware token selection possible:

- `getEditingBlockContent` at handler time has the typed `/URL+ …` text
  **already removed**.
- `getEditingCursorPosition().pos` indexes **that cleaned string**, not the
  pre-removal one.
- Both are **stable** - re-reading inside `setTimeout(…, 0)` returns identical
  values, so nothing settles on a later tick.

Verified at five caret positions: after a mid-block URL, inside a URL, column
0, end of block, and on line 2 of a multi-line block.

The reason it works is visible in the registry. `@logseq/libs` rewrites a
callback slash command into an ordered action list, which you can read back
from a running app:

```
node script/logseq-cdp.mjs eval "(()=>{const a=logseq.api.get_state_from_store('plugin/installed-slash-commands');const c=a['logseqUrlPlusDev'];return JSON.stringify(c[Object.keys(c)[0]]);})()"

[["clearCurrentSlash", false, {...}], ["restoreSavedCursor", {...}], ["hook", ...]]
```

Logseq clears the typed text and restores the cursor **before** calling the
plugin. Anything that reorders those steps invalidates the feature.

**Two behaviours to know when testing by hand or by script:**

- **Logseq only opens the command menu when `/` follows a space** (or the start
  of the block). Typing `/` straight after a URL inserts a literal slash and no
  menu appears. Mid-block invocation therefore always costs one typed space.
- **That space survives** `clearCurrentSlash`, which removes only the command
  text. So a mid-block invocation leaves the block one space wider than it
  started, and anything reconstructing the block has to account for it.

### Driving keystrokes over CDP

`script/logseq-cdp.mjs type <text>` and `key <Name>` dispatch through the CDP
Input domain, which is what made the above measurable without a human at the
keyboard. Three things that cost time getting there:

- **`keyDown` must not carry `text`.** Both `keyDown`-with-text and `char`
  insert, so sending both types every character twice.
- **`code` and `windowsVirtualKeyCode` are required.** Logseq opens the command
  menu from a keydown handler that inspects the key code; a bare `char` event
  inserts the `/` and the menu never appears.
- **The window must be focused.** A backgrounded Logseq reports
  `document.visibilityState === "hidden"` and refuses to enter editing mode, so
  `editBlock` silently does nothing. Fix with
  `osascript -e 'tell application "Logseq" to activate'` first.

Place the caret exactly with `logseq.api.edit_block(uuid, {pos: n})` rather
than clicking coordinates.

### What you can and cannot verify

Slash-command handlers are wired by the SDK as events *inside* the plugin
sandbox (`Editor["on" + hookName]`), so they cannot be fired from the host with
`caller.call` or `callUserModel`. They **can** be driven with synthetic input -
see [Driving keystrokes over CDP](#driving-keystrokes-over-cdp) - which is
enough to exercise a command end to end and read the result back:

```
osascript -e 'tell application "Logseq" to activate'
node script/logseq-cdp.mjs eval "logseq.api.edit_block('<uuid>',{pos:29})"
node script/logseq-cdp.mjs type " /URL+ [title](url)"    # note the leading space
node script/logseq-cdp.mjs key Enter
```

What that still does **not** cover: whether a command chosen with the **mouse**
behaves like one chosen with Enter, and anything about how the UI actually
looks. Take a screenshot and read it; do not infer appearance from state. The
Inspector was once completely broken while every test passed, because no test
renders a component.

Hand the [manual checklist](#manual-smoke-checklist) to a human before a
release regardless. Automation proves a path works; it does not prove the
feature is usable.

---

## Part 3 — Human coders

### The REPL, via Calva

Check `bb repl-status` first — the readiness rules in
[Part 2](#check-repl-readiness-before-evaluating) apply just as much here.
Then connect Calva to the shadow-cljs nREPL on port 8702, build `:plugin`.

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

Editor setup: VS Code +
[Calva](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva),
whose [Paredit](https://calva.io/paredit/) is what keeps parens balanced through
structural edits rather than by hand.

### Manual smoke checklist

Run before tagging a release. This is the only coverage for the two failure
classes the [test tiers cannot reach](#testing), and steps 1-8 all need real
keystrokes. Start the watch, `bb sideload`, then in a scratch block:

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
8. `bb reload` -> each slash command appears **once** (12 total).
9. Multi-URL block: `alpha https://example.com middle https://github.com omega`.
   Put the cursor at the **end of** the first URL, run `/URL+ [title](url)` ->
   the first is formatted and the rest of the block is untouched. Repeat at the
   end of the second. (Mid-URL does not work by design: Logseq needs a space
   before `/`, which splits the URL.)
10. `see https://example.com for details`, cursor at the end -> the sole URL is
    used even though the cursor is on a word.
11. `URL+ All links in block` on a block mixing a bare URL, an existing
    markdown link and an unreachable URL -> only the bare one is converted and
    the count reports honestly.
