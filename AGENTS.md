# Repository Guidelines

URL+ is a **Logseq plugin written in ClojureScript**, built with shadow-cljs,
UI in Rum, styled with Tailwind + daisyUI, tasks run by Babashka.
There is no TypeScript and no JS bundler config — `package.json` exists only to
declare npm deps and the Logseq plugin manifest.

## Prerequisites

- **A JDK 21 or newer must be installed**, because shadow-cljs bundles a
  Closure Compiler built for class-file version 65. You do not need to export
  `JAVA_HOME`: the `bb` tasks resolve a suitable JDK themselves and will look
  past an older system default. `bb java` shows which one they picked.
- Node (CI uses 18 today; 22 is the target), Yarn, Babashka, clj-kondo.
- `yarn install` before any build — `node_modules/` is not committed.

## Build, Test, and Development Commands

Everything runs through `bb`; `bb tasks` lists them all. The tasks are written
to be driven by a coding agent as well as by a person, so they are
non-interactive, safe to re-run, and report status through exit codes.

| Task | Purpose | Exit code |
| --- | --- | --- |
| `bb doctor` | Check the toolchain; prints what is missing and how to fix it | 1 if anything missing |
| `bb java` | Which JDK the tasks resolved | 0 |
| `bb ci` | Everything CI runs: lint, check-css, test, build | 1 on any failure |
| `bb lint` | clj-kondo over `src` | 1 on any warning |
| `bb check-css` | Every daisyUI class used by the UI still exists | 1 if any are gone |
| `bb test` | Unit + integration suites | 1 on failure |
| `bb build` | Release bundle into `dist/` (`:advanced`) | 1 on failure |
| `bb dev` | Watch CLJS + Tailwind, re-running tests on save — **blocks** | 1 if already running |
| `bb dev-start` | Same watch, **backgrounded**, waits until ready | 0 when ready |
| `bb dev-logs` | Show the background watch log | 0 |
| `bb repl-status` | Is the `:plugin` CLJS runtime attached? | **0 = ready**, 1 = not |
| `bb stop` | Stop the shadow-cljs server (idempotent) | 0 |
| `bb restart` | `stop` then `dev` | — |
| `bb sideload` | Create/refresh the dev plugin copy and register it with a running Logseq | 1 if `dist/` is missing |
| `bb reload` | Reload the side-loaded plugin; prints the slash-command count | 1 if Logseq is unreachable |
| `bb deps` | Check for Node + Clojure dependency updates | **1 = updates available** |
| `bb release` | Runs `bb ci`, then tags and pushes | 1 if CI fails |

**If you are an agent, use `bb dev-start`, not `bb dev`.** `bb dev` is a watch
process that never returns and will hang your tool call. `bb dev-start` starts
the same watch detached, waits until both builds report ready, prints a status
block and exits 0 — typically in under ten seconds. Then poll `bb repl-status`.

You do not need to set `JAVA_HOME`; see Prerequisites.

## REPL-Driven Development

The plugin's CLJS runtime lives **inside a Logseq iframe**. It does not exist
until Logseq is running with the plugin loaded. Keep three states distinct:

1. **server/watch alive** — a shadow-cljs worker exists for the build
2. **build ready** — that worker compiled successfully
3. **runtime attached** — a live JS runtime is connected

```bash
bb repl-status   # runtimes=0 means do NOT attach or evaluate yet
```

If you evaluate CLJS while `runtimes=0`, you are talking to the **JVM Clojure**
REPL instead, and the results will be confusing. This is the single most common
cause of "the REPL is behaving strangely" in this repo.

Workflow:

1. `bb dev`
2. In Logseq: enable developer mode → Plugins → Load unpacked plugin → repo root
   (uninstall the Marketplace copy first, if present)
3. `bb repl-status` → confirm `:plugin runtimes=1`
4. Connect Calva to the shadow-cljs nREPL on port **8702** (`:init-ns core`)
5. Rich `(comment ...)` blocks at the end of `ui.cljs` and `ls.cljs` hold
   scratch expressions for reload and state inspection

Hot reload (`:after-load core/reload`) is unreliable under Logseq dev mode —
see the note in `shadow-cljs.edn`. Reload the plugin from Logseq's plugin panel
when state gets stuck.

## Coding Style & Conventions

- Run `bb lint` after every save and fix all warnings before proceeding.
- **Never hand-balance parens.** Use structural editing (Calva/Paredit) or a
  structural tool; do not count brackets manually.
- Every namespace gets a docstring naming its single responsibility.
- Keep pure logic in `util.cljs` / `api.cljs` / `feat/` where it is unit-testable;
  `core.cljs` orchestrates, `ls.cljs` is the only Logseq interop layer, and
  `entry.cljs` is the only namespace that imports `@logseq/libs`.
- `feat/define.cljs` is the model to follow: small, pure, well covered.

## Testing

Two tiers, both run by `bb test`:

1. **Unit specs** in `src/test/*_spec.cljs` for pure functions.
2. **Integration** (`integration_spec.cljs` + `harness.cljs`) runs the real
   `core/handle-slash-cmd` against a fake `js/logseq` and a fixture-backed
   `js/fetch`, asserting on the exact block content that would reach the graph.

This works only because `core` and `ls` no longer import `@logseq/libs` - that
import lives in `entry.cljs`, the build's `:init-fn`. Keep it that way: pulling
`@logseq/libs` back into `core` or `ls` breaks the whole integration tier,
because it needs browser globals at import time and cannot load under Node.

link-preview-js fetches through its own transport, so metadata is injected by
swapping `core/fetch-link-preview` (see `harness/link-preview-fixture`).
`with-redefs` does not work here - it restores synchronously, long before the
async pipeline reaches the call.

Run `bb lint && bb check-css && bb test` before every commit; keep all green.

## Release

- Targets **Logseq OG** (file-based markdown graphs), via `@logseq/libs` 0.0.17.
  The database version needs `@logseq/libs@next` and a different API surface —
  treat it as a separate port, not an upgrade.
- Bump `version` in `package.json`, then `bb release`.

## Gotchas

- `ls.cljs` reaches Logseq API methods through their owning object at call time
  (`(.showMsg (.-UI js/logseq) msg)`). Do not go back to capturing them with
  `def` at namespace load: that detaches `this` and breaks under Node.
- Property names survive `:advanced` only because `:infer-externs :auto`
  preserves them — re-check `dist/index.js` after any `@logseq/libs` or
  shadow-cljs upgrade.
- Top-level `logseq.*` methods cannot be aliased at all; call them directly.
- `util/decode-html-content` uses the DOM when one is available and falls back
  to a plain entity decode otherwise. Keep the fallback: it is what lets the
  integration tier run.
- **Do not upgrade shadow-cljs past 3.1.2** while rum is 0.12.11. 3.5.2 breaks
  `rum/defc` argument passing - components receive the raw `arguments` object -
  which silently destroys the Inspector UI. No test catches it; nothing renders
  a component. rum 0.12.11 is the latest and upstream is dormant.
- Hot reload does not reach the plugin. Run `bb reload` after every change.
- Tailwind's `--watch` exits when stdin is not a TTY, silently producing no
  `dist/styles.css`. `bb dev` uses `--watch=always`; do not "simplify" it back.
- `Browserslist: caniuse-lite is outdated` comes from inside the tailwindcss
  package and cannot be fixed without upgrading Tailwind. Do not run
  `update-browserslist-db` here — it removes packages and silences nothing.
- daisyUI renames classes between majors and a dropped class fails silently.
  `bb check-css` guards this; five classes had been dead since Dec 2023 before
  it existed.
- Tailwind scans `dist/**/*.{html,js}`, i.e. compiled output, so Tailwind must
  run after the CLJS build. `bb build` already orders this correctly.
