# Repository Guidelines

URL+ is a **Logseq plugin written in ClojureScript**, built with shadow-cljs,
UI in Rum, styled with Tailwind + daisyUI, tasks run by Babashka.
There is no TypeScript and no JS bundler config — `package.json` exists only to
declare npm deps and the Logseq plugin manifest.

## Prerequisites

- **JDK 21 or newer is required.** shadow-cljs 3.1.2 bundles a Closure Compiler
  built for class-file version 65. On JDK 17 every build fails with
  `UnsupportedClassVersionError`. CI already pins JDK 21; set `JAVA_HOME`
  locally, e.g. `export JAVA_HOME=/opt/homebrew/opt/openjdk`.
- Node (CI uses 18 today; 22 is the target), Yarn, Babashka, clj-kondo.
- `yarn install` before any build — `node_modules/` is not committed.

## Build, Test, and Development Commands

- `bb tasks` lists every task; `bb.edn` is the source of truth, not npm scripts.
- `bb test` runs the unit suite (`:node-test` target, `:autorun true`).
- `bb build` produces the release bundle in `dist/` (`:advanced` optimized).
- `bb dev` watches CLJS + Tailwind in parallel and re-runs tests on save.
- `bb lint` runs clj-kondo over `src`. **Keep this at zero warnings.**
- `bb check-css` verifies every daisyUI class used by the UI still exists.
- `bb repl-status` reports REPL readiness — see below.
- `bb release` tags the version in `package.json` and pushes, triggering the
  GitHub release workflow.

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
- daisyUI renames classes between majors and a dropped class fails silently.
  `bb check-css` guards this; five classes had been dead since Dec 2023 before
  it existed.
- Tailwind scans `dist/**/*.{html,js}`, i.e. compiled output, so Tailwind must
  run after the CLJS build. `bb build` already orders this correctly.
