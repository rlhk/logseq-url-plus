# Logseq Plugin Development in ClojureScript

### General Information

- [Official plugin samples](https://github.com/logseq/logseq-plugin-samples)
- [@logseq/libs](https://logseq.github.io/plugins/)

### Development in ClojureScript

This shadow-cljs project is created by following: https://github.com/thheller/shadow-cljs#quick-start. Notes below:

> **NOTE:** The following assumes macOS development environment.

#### Project Creation
New project is created from shadow-cljs boilerplate

`npx create-cljs-project logseq-url-plus`
> **NOTE:** This is a onetime operation. Skip if project is already created.

If the codebase is obtained from a git repository, run `yarn` to install Node.js dependencies

#### shadow-cljs
shadow-cljs setup could be verified by launching the browser REPL

`npx shadow-cljs browser-repl`

On MacOS, a browser will be opened to provide the CLJS runtime. Input `(js/alert "Hello World)` in the REPL and a classic alert box will be shown in the browser window.

**A JDK 21 or newer must be installed.** shadow-cljs bundles a Closure Compiler
built for class-file version 65, so an older JVM dies before compiling anything:

```
UnsupportedClassVersionError: com/google/javascript/jscomp/CompilerOptions
has been compiled by a more recent version of the Java Runtime (class file
version 65.0), this version of the Java Runtime only recognizes class file
versions up to 61.0
```

You do **not** need to export `JAVA_HOME`. The `bb` tasks locate a suitable JDK
themselves, checking `JAVA_HOME` first and then the usual install locations
(`/Library/Java/JavaVirtualMachines`, Homebrew, SDKMAN). A system default of
JDK 17 is fine — the tasks will look past it.

```
bb java     # which JDK the tasks will use, and what else is available
```

If nothing suitable is found, every task prints a warning saying so.
`brew install openjdk` is enough to fix it.

Of the qualifying JDKs the tasks pick the **lowest**, which keeps local builds
on the same version CI uses (both workflows pin 21). On JDK 24+ the Closure
Compiler's bundled protobuf emits a `sun.misc.Unsafe` deprecation warning on
every build; the tasks suppress it with
`--sun-misc-unsafe-memory-access=allow`, which is why you may see a one-line
`NOTE: Picked up JDK_JAVA_OPTIONS`. That flag does not exist before JDK 24, so
it is applied only when the selected JDK is new enough.

#### Development

`bb dev`

> **NOTE:** Moved to babashka based tasks. Old npm scripts are kept as reference.

The dev task do the following things:
1. Watch code changes and perform compilation if touched
2. Watch and run tests upon code or unit test code change

> **NOTE:** Current Test Driven Development (TDD) setup runs on Node.js runtime.

Check `bb.edn` or run `bb tasks` to list all available tasks. The ones used
most:

| Task | Purpose |
| --- | --- |
| `bb doctor` | Check the toolchain and report what is missing |
| `bb dev` | Watch CLJS + Tailwind, re-running tests on save (blocks) |
| `bb dev-start` | The same watch, backgrounded, waits until ready |
| `bb stop` / `bb restart` | Stop or replace a running watch |
| `bb repl-status` | Is the `:plugin` CLJS runtime attached? |
| `bb ci` | Everything CI runs: lint, check-css, test, build |
| `bb test` | Unit + integration suites |
| `bb build` | Release bundle into `dist/` |
| `bb lint` | clj-kondo over `src` — keep at zero warnings |
| `bb check-css` | Verify every daisyUI class used by the UI still exists |
| `bb deps` | Check for dependency updates |
| `bb release` | Run CI, then tag and push |

The tasks are meant to serve coding agents as well as people: they are
non-interactive, safe to re-run, and signal state through exit codes rather
than only prose. `bb repl-status` exits 0 only when the `:plugin` runtime is
actually attached, and `bb dev-start` exists because `bb dev` is a watch that
never returns — it would hang an agent's tool call.

Starting a second watch used to fail with a 45-line `ExceptionInfo: already
started` stack trace from inside shadow-cljs; `bb dev` now checks first and
tells you what to do instead.

`bb check-css` exists because daisyUI renames classes between majors and a
dropped class fails silently — the markup still renders, just unstyled. Five
classes had been dead since Dec 2023 before this check was added.

In the Logseq App

- Enable developer mode in Logseq
- Click "Load unpacked plugin" and open the root folder of this project which contains the `package.json` and `dist` folder. Logseq plugin system requires entry `package.json` even in dev mode
- To open Logseq console for debugging, use Chrome's default hotkey. E.g. `Option Command + i` on MacOS. For more information, see https://www.electronjs.org/docs/latest/tutorial/application-debugging

#### Editor Setup

- [Visual Studio Code - VSCode](https://code.visualstudio.com)
- [VSCode Neovim](https://marketplace.visualstudio.com/items?itemName=asvetliakov.vscode-neovim)
- [Calva](https://marketplace.visualstudio.com/items?itemName=betterthantomorrow.calva)
  - [Paredit](https://calva.io/paredit/) in [Calva](https://calva.io)

#### REPL readiness — check before attaching

The plugin's CLJS runtime lives **inside a Logseq iframe**, so it does not exist
until Logseq is running with the plugin loaded. Three states are easy to
conflate; keep them separate:

1. **server/watch alive** — a shadow-cljs worker exists for the build
2. **build ready** — that worker compiled successfully
3. **runtime attached** — a live JS runtime is connected

```
bb repl-status
```

```
shadow-cljs server : running
:plugin             watch=running     runtimes=1
:test               watch=running     runtimes=1
```

If `:plugin runtimes=0`, do **not** attach or evaluate yet — you would be
talking to the JVM Clojure REPL instead of the plugin, and the results will be
confusing. Start `bb dev` and load the unpacked plugin in Logseq first.

#### REPL Setup in VSCode with Calva

Calva's REPL client can connnect to the REPL server provided by the shadow-cljs dev mode runtime in Logseq App.
- Make sure Logseq Desktop App developer mode is enabled and `bb dev` is running as mentioned above
- Uninstall the plugin installed from Marketplace, if applicable
- In Logseq App
  - Select: Plugins -> Load unpacked plugin -> "Choose the plugin project folder"
  - Test the plugin is actually working

Open the plugin project in VSCode. Bring up the command search and proceed with the following options:

![](./imgs/calva-repl-1.png)

![](./imgs/calva-repl-2.png)

![](./imgs/calva-repl-3.png)

![](./imgs/calva-repl-4.png)

Try evaluate a few forms in the REPL. 

`(in-ns 'core)` switch to namespace `core`

`config/slash-commands` print the registered slash commands

![](./imgs/calva-repl-5.png)

`(js/alert "Hello")`

![](./imgs/calva-repl-6.png)

`(js/console.log "Hello Console")`
![](./imgs/calva-repl-7.png)

`(ls/show-msg "Hello Logseq")` Run the interop fn `show-msg` - it lives in the
`ls` namespace, not `core` - to display a Logseq App message.

Now the REPL is ready for action!

#### Manual reload

In case the hot reload does not fully reflect recent code change, or the app state is stuck, the rich comment block at the end of `ui.cljs` contain expressions which might help.

Given a REPL Setup in VSCode as specified above, evaluating expressions can be done by placing the cursor inside the expression and pressing `option + enter`.

### Marketplace

#### Version Release

- Update the "version" field in `package.json`
- `bb release`
  - The babashka task reads the "version" field in `package.json` and add it as a new git tag. Upon tag pushing to GitHub, GitHub workflow will build a new release to be picked up by Logseq marketplace.

#### New Marketplace Submission

- Read the [Official Marketplace README](https://github.com/logseq/marketplace/blob/master/README.md)
- Fork `https://github.com/logseq/marketplace`
- Update files in `https://github.com/rlhk/marketplace/tree/master/packages/logseq-url-plus`
- Create pull request (PR)

### Testing

`bb test` runs both tiers of the suite (shadow-cljs `:node-test`, `:autorun true`):

- **Unit** — pure functions in `src/test/*_spec.cljs`.
- **Integration** — `integration_spec.cljs` drives the real
  `core/handle-slash-cmd` against the fakes in `harness.cljs`: a `js/logseq`
  stub recording every Editor/UI call, and a fixture-backed `js/fetch`.
  Assertions are made on the exact block content that would be written.

Neither tier loads the built bundle into Logseq, so neither can catch a release
that fails on load - the `:advanced` build munges every name, and `ls.cljs`
reaches Logseq's API by property access that survives only because
`:infer-externs :auto` preserves it. The manual checklist below is the only
coverage for that.

A third tier - true end-to-end against a running Logseq - is not implemented.
Logseq's own suite ([`clj-e2e`](https://github.com/logseq/logseq/tree/master/clj-e2e))
uses Wally over Playwright Java driven by Babashka, which would fit this repo's
tooling, but there is no published way to load an *unpacked* plugin under
automation. Settling that needs a timeboxed spike: either confirm plugins load
in the HTTP-served app, or drive the desktop binary with Playwright's
`_electron.launch` and side-load via `LSPluginCore.register(...)`.

#### Manual smoke checklist

Run before tagging a release. `bb dev`, load the unpacked plugin, then in a
scratch block:

1. `https://youtu.be/dQw4w9WgXcQ` + `/URL+ [title](url)` -> resolves to the real
   video title. This is the regression that 0.2.0 exists to fix.
2. A `bit.ly` or `t.co` link -> resolves rather than throwing.
3. `https://jsonplaceholder.typicode.com/posts/1` +
   `/URL+ API -> JSON Code` -> JSON block. Confirm in DevTools that a metadata
   command issues **one** request, not two.
4. An unreachable host -> a visible message, no unhandled rejection in console.
5. `/URL+ Append Word Definition` on `prodigy` -> formatted definition;
   on a nonsense word -> a "no definition found" message.
6. `/URL+ Inspector ...` -> modal opens, all three tabs render, Esc and
   backdrop-click close it, Confirm writes the block.
7. Empty block + any command -> graceful message, no throw.
8. Reload the plugin from Logseq's plugin panel -> each slash command appears
   **once**.

### TODOs
- [x] Use shadow-cljs advanced compilation in release for release bundle size optimization
- [x] Move logseq/libs from index.html to `ns require` when clojure compiler issue is resolved: https://github.com/thheller/shadow-cljs/issues/1061. The issue was fixed as of @logseq/libs version 0.0.11
  - As of 0.2.0 that import lives in `entry.cljs`, the build's `:init-fn`, and
    nowhere else. Importing it installs the `logseq` global as a side effect and
    needs browser globals, so keeping it out of `core` and `ls` is what lets
    those namespaces load under Node for the integration tier.

### Library Management

- Run `bb deps` to check dependency updates for both Node and Clojure/Script libraries 
  - The bb task uses [Antq](https://github.com/liquidz/antq) to find outdated Clojure/Script libraries
  - Follow official setup to modify `$HOME/.clojure/deps.edn`
- Modify `shadow-cljs.edn` to update dependencies

### Reference Repositories

- https://github.com/logseq/logseq-plugin-samples (official sample)
- https://github.com/pengx17/logseq-plugin-link-preview
- https://github.com/0x7b1/logseq-plugin-automatic-url-title
- https://github.com/trashhalo/logseq-dictionary
- https://github.com/kurtharriger/logseq-things3-plugin (ClojureScript but not in marketplace)
