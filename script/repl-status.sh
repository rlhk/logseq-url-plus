#!/bin/sh
# Report shadow-cljs REPL readiness.
#
# Written to be read by both people and coding agents:
#   - stdout is a stable, greppable key=value-ish table
#   - exit 0  the :plugin CLJS runtime is attached; safe to evaluate CLJS
#   - exit 1  not ready (no server, no watch, or no runtime attached)
#
# Distinguishes the three states that are easy to conflate (pattern borrowed
# from Logseq's own .agents/skills/logseq-repl):
#
#   1. server/watch alive  - a shadow-cljs worker exists for the build
#   2. build ready         - that worker compiled successfully
#   3. runtime attached    - a live JS runtime is connected
#
# For this plugin the CLJS runtime lives inside a Logseq iframe, so :plugin
# runtimes stay at 0 until Logseq is running with the plugin loaded. Evaluating
# before then silently targets the JVM Clojure REPL instead.

set -e

OUT=$(npx --yes shadow-cljs clj-eval "
(require '[shadow.cljs.devtools.api :as api]
         '[shadow.cljs.devtools.server.runtime :as runtime])
(if-not (runtime/get-instance)
  (println \"server=not-running\")
  (do
    (println \"server=running\")
    (doseq [build [:plugin :test]]
      (let [running? (try (api/worker-running? build) (catch Exception _ false))
            runtimes (if running?
                       (try (count (api/repl-runtimes build)) (catch Exception _ 0))
                       0)]
        (println (format \"%s watch=%s runtimes=%s\"
                         (str build)
                         (if running? \"running\" \"not-running\")
                         runtimes))))))
" 2>/dev/null | grep -vE '^nil$|^shadow-cljs - ')

echo "$OUT"

# Ready when the :plugin build has at least one attached runtime.
if echo "$OUT" | grep -qE '^:plugin .*runtimes=[1-9]'; then
  echo "ready=yes"
  exit 0
fi

echo "ready=no"
if echo "$OUT" | grep -q 'server=not-running'; then
  echo "hint: run 'bb dev' (or 'bb dev-start'), then load the unpacked plugin in Logseq."
else
  echo "hint: load the unpacked plugin in Logseq; the CLJS runtime lives in its iframe."
fi
exit 1
