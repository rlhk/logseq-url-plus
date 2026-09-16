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

**JDK 21+ is required.** shadow-cljs 3.1.2 bundles a Closure Compiler built for
class-file version 65, so building on JDK 17 fails with:

```
UnsupportedClassVersionError: com/google/javascript/jscomp/CompilerOptions
has been compiled by a more recent version of the Java Runtime (class file
version 65.0), this version of the Java Runtime only recognizes class file
versions up to 61.0
```

Set `JAVA_HOME` before building, for example:

```
export JAVA_HOME=/opt/homebrew/opt/openjdk   # JDK 21 or newer
```

CI already pins JDK 21 (see `.github/workflows/publish.yml`), so this affects
local development only. To manage multiple JDKs, see https://github.com/jenv/

#### Development

`bb dev`

> **NOTE:** Moved to babashka based tasks. Old npm scripts are kept as reference.

The dev task do the following things:
1. Watch code changes and perform compilation if touched
2. Watch and run tests upon code or unit test code change

> **NOTE:** Current Test Driven Development (TDD) setup runs on Node.js runtime.

Check `bb.edn` or run `bb tasks` to list all available tasks

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

`commands` print the defined commands symbol

![](./imgs/calva-repl-5.png)

`(js/alert "Hello")`

![](./imgs/calva-repl-6.png)

`(js/console.log "Hello Console")`
![](./imgs/calva-repl-7.png)

`(show-msg "Hello Logseq")` Run the defined Clojure fn `show-msg` in namespace `core` to display Logseq App message.

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

### TODOs
- [x] Use shadow-cljs advanced compilation in release for release bundle size optimization
- [x] Move logseq/libs from index.html to `ns require` when clojure compiler issue is resolved: https://github.com/thheller/shadow-cljs/issues/1061. The issue was fixed as of @logseq/libs version 0.0.11

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
