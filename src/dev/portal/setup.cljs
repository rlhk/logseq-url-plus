(ns portal.setup
  ;; https://github.com/PEZ/shadow-portal/blob/master/env/dev/portal/setup.cljs
  "Experimental setup. Not used."
  (:require [portal.shadow.remote :as shadow]))

(defn submit [value]
  (shadow/submit
   (cond
     (:portal.nrepl/eval (meta value)) value
     (implements? IWithMeta value) (with-meta value {:portal.viewer/default :portal.viewer/tree})
     :else value)))

(add-tap #'submit)

(comment
  (require '[portal.web :as p])
  (shadow/get-port)
  (tap> :foo)
  )