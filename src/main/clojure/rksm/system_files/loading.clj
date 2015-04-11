(ns rksm.system-files.loading
  (:require [rksm.system-files.cljx :as cljx]))

(defn require-ns
  [ns-sym & [file-name]]
  (try 
    (require ns-sym :reload)
    (catch java.io.FileNotFoundException e
      (cljx/require-ns ns-sym file-name))))
