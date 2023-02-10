(ns portal.setup
  ;; https://github.com/PEZ/shadow-portal/blob/master/env/dev/portal/setup.cljs
  ;; https://cljdoc.org/d/djblue/portal/0.35.1/doc/guides/shadow-cljs?q=shortcut#shadow-cljs
  "Experimental setup. Not used."
  (:require 
   [portal.shadow.remote :as r]
   [portal.web :as p]))

(defn- submit [value]
  (p/submit value)
  (r/submit value))

(defn- error->data [ex]
  (merge
   (when-let [data (.-data ex)]
     {:data data})
   {:runtime :portal
    :cause   (.-message ex)
    :via     [{:type    (symbol (.-name (type ex)))
               :message (.-message ex)}]
    :stack   (.-stack ex)}))

(defn- async-submit [value]
  (cond
    (instance? js/Promise value)
    (-> value
        (.then async-submit)
        (.catch (fn [error]
                  (async-submit error)
                  (throw error))))

    (instance? js/Error value)
    (submit (error->data value))

    :else
    (submit value)))

(add-tap async-submit)

(defn- error-handler [event]
  (tap> (or (.-error event) (.-reason event))))

(.addEventListener js/window "error" error-handler)
(.addEventListener js/window "unhandledrejection" error-handler)

#_(p/set-defaults! {:theme :portal.colors/gruvbox})
#_(add-tap p/submit)

#_(defn submit [value]
  (shadow/submit
   (cond
     (:portal.nrepl/eval (meta value)) value
     (implements? IWithMeta value) (with-meta value {:portal.viewer/default :portal.viewer/tree})
     :else value)))

#_(add-tap #'submit)

(comment
  (require '[portal.web :as p])
  (p/set-defaults! {:theme :portal.colors/gruvbox})
  (add-tap p/submit)
  (r/get-port)
  (tap> :foo)
  )