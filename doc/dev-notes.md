# Logseq Plugin Development in ClojureScript

### General Information

- [Official plugin samples](https://github.com/logseq/logseq-plugin-samples)
- [@logseq/libs](https://logseq.github.io/plugins/)

### Development in ClojureScript

This shadow-cljs project is created by following: https://github.com/thheller/shadow-cljs#quick-start. Notes below:

1. New project is created from shadow-cljs boilerplate

   `npx create-cljs-project logseq-url-plus`
   > **NOTE:** This is a onetime operation. Skip if project is already created.

2. If the codebase is obtained from a git repository, run `yarn install` to install Node.js dependencies

3. Verify shadow-cljs setup by launching browser REPL

   `npx shadow-cljs browser-repl`

   On MacOS, a browser will be opened to provide the CLJS runtime. Input `(js/alert "Hello World)` in the REPL and a classic alert box will be shown in the browser window.

   In case of Java version related errors, you might want to manage Java with https://github.com/jenv/

4. Development (see `package.json` scripts section for detail)
   `yarn dev`
   - Enable developer mode in Logseq
   - Click "Load unpacked plugin" and open the ./dist folder
   - To open Logseq console for debugging, use Chrome's default hotkey. E.g. `Option Command + i` on MacOS. For more information, see https://www.electronjs.org/docs/latest/tutorial/application-debugging

   > **NOTE:** Test Driven Development (TDD) setup runs on Node.js runtime.

### Marketplace Submission

- [Official Marketplace README](https://github.com/logseq/marketplace/blob/master/README.md)
- Create version tag
  `git tag -a <version-no> -m "<version-no>"`
  `git push origin <version-no>`
- Fork `https://github.com/logseq/marketplace`
- Update files in `https://github.com/rlhk/marketplace/tree/master/packages/logseq-url-plus`
- Create pull request (PR)

### TODO
- [x] shadow-cljs advanced compilation for optimizing release bundle size
