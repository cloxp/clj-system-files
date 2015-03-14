(ns rksm.system-files.cljx
  (:require (cljx core rules)
            [rksm.system-files :refer [file-for-ns ns-name->rel-path source-for-ns]]
            [clojure.java.io :as io])
  (:import (clojure.lang Compiler)
           (java.io StringReader)))

(defn require-ns
  [ns-name & [full-file-name]]
  (let [ext-match #".cljx$"
        full-file-name (or (file-for-ns ns-name full-file-name ext-match))
        relative-name (ns-name->rel-path ns-name ".cljx")
        src (source-for-ns ns-name full-file-name ext-match)
        x-src (cljx.core/transform src cljx.rules/clj-rules)]
    (binding [*file* relative-name]
      (Compiler/load
       (StringReader. x-src) relative-name
       (.getName (io/file relative-name))))))