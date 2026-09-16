(ns shadow.user
  "REPL entry point for a plain nREPL client.

  Connecting to shadow-cljs's nREPL yields a JVM Clojure REPL; `cljs-repl`
  piggiebacks from there into the CLJS runtime. The runtime lives inside a
  Logseq iframe, so it only exists once Logseq is running with the plugin
  loaded - check `bb repl-status` first."
  (:require [shadow.cljs.devtools.api :as api]))

(defn cljs-repl
  "Watch and attach a CLJS REPL to the :plugin build.

  Previously referenced build id :app, which does not exist in this project -
  the builds are :plugin and :test - so this never worked."
  []
  (api/watch :plugin)
  (api/repl :plugin))
