(ns rksm.system-files.loading
  (:require [rksm.system-files.cljx :as cljx]))

(defn require-ns
  [ns-sym & [file-name]]
  (if (and file-name (re-find #"\.cljs$" (str file-name)))
    (do
      ; FIXME... dependency to cloxp-cljs!!?
      (require 'rksm.cloxp-cljs.ns.internals)
      (rksm.cloxp-cljs.ns.internals/ensure-ns-analyzed! ns-sym file-name))
    (try 
      (require ns-sym :reload)
      (catch java.io.FileNotFoundException e
        (cljx/require-ns ns-sym file-name)))))
