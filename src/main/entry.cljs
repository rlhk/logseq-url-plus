(ns entry
  "Plugin entry point for the :plugin build.

  Importing @logseq/libs installs the `logseq` global as a side effect, so the
  import has to happen somewhere. Keeping it here - rather than in `ls` or
  `core` - means those namespaces can load in a plain Node runtime where no
  Logseq global exists, which is what lets the integration harness exercise
  the real command pipeline against a fake `js/logseq`."
  (:require
   ["@logseq/libs"]
   [core]))

(defn init
  "shadow-cljs :init-fn"
  []
  (core/init))

(defn reload
  "shadow-cljs :after-load hook"
  []
  (core/reload))
