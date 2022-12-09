(ns helper
  (:require [babashka.process :refer [shell]]))

(defn run [& cmds] (doseq [cmd cmds] (shell cmd)))

(comment
  (run "pwd" "whoami")
  )
