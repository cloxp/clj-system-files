(ns rksm.system-files.loading
  (:require [rksm.system-files.cljx :as cljx]))

(defn require-ns
  [ns-name & [file-name]]
  (try 
    (require ns-name :reload)
    (catch java.io.FileNotFoundException e
      (cljx/require-ns ns-name file-name))))
