#!/bin/sh
# Report shadow-cljs REPL readiness for each build.
#
# Distinguishes the three states that are easy to conflate (pattern borrowed
# from Logseq's own .agents/skills/logseq-repl):
#
#   1. server/watch alive - a shadow-cljs worker exists for the build
#   2. build ready        - that worker has compiled successfully
#   3. runtime attached   - a live JS runtime is connected
#
# For this plugin the CLJS runtime lives inside a Logseq iframe, so :plugin
# runtimes stay at 0 until Logseq is running with the plugin loaded. Do not
# attach a REPL or evaluate CLJS until the count is > 0 - otherwise you are
# talking to the JVM Clojure REPL and the results will be confusing.

set -e

npx shadow-cljs clj-eval "
(require '[shadow.cljs.devtools.api :as api]
         '[shadow.cljs.devtools.server.runtime :as runtime])
(if-not (runtime/get-instance)
  (do (println \"shadow-cljs server : NOT running\")
      (println)
      (println \"Hint: run 'bb dev', then load the unpacked plugin in Logseq.\"))
  (do
    (println \"shadow-cljs server : running\")
    (doseq [build [:plugin :test]]
      (let [running? (try (api/worker-running? build) (catch Exception _ false))
            runtimes (if running?
                       (try (count (api/repl-runtimes build)) (catch Exception _ 0))
                       0)]
        (println (format \"%-19s watch=%-11s runtimes=%s\"
                         (str build)
                         (if running? \"running\" \"not-running\")
                         runtimes))))
    (when (zero? (try (count (api/repl-runtimes :plugin)) (catch Exception _ 0)))
      (println)
      (println \"No :plugin runtime attached - load the unpacked plugin in Logseq before evaluating CLJS.\"))))
" | grep -v '^nil$'
