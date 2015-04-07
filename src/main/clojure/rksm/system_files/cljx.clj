(ns rksm.system-files.cljx
  (:require (cljx core rules)
            [rksm.system-files :refer [file-for-ns ns-name->rel-path source-for-ns file]]
            [clojure.java.io :as io])
  (:import (clojure.lang Compiler)
           (java.io StringReader File)))

(defn require-ns
  [ns-name & [full-file-name]]
  (let [ext-match #".cljx$"
        full-file-name (or (file-for-ns ns-name full-file-name ext-match))]
    (if-not full-file-name (throw (java.io.FileNotFoundException.
                                   (str "Cannot locate cljx file for namespace " ns-name))))
    (let [relative-name (ns-name->rel-path ns-name ".cljx")
          src (source-for-ns ns-name full-file-name ext-match)
          x-src (cljx.core/transform src cljx.rules/clj-rules)]
      (binding [*file* relative-name]
        (Compiler/load
         (StringReader. x-src) relative-name
         (.getName (io/file relative-name)))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; rk 2015-03-16:
; This is taken from cljx.repl-middleware, will require changes once features
; are supported in Clojure 1.7

(defn- find-resource
  [name]
  (if-let [cl (clojure.lang.RT/baseLoader)]
    (.getResource cl name)
    (ClassLoader/getSystemResourceAsStream name)))

; clojure.core/load from ~Clojure 1.6.0
; clojure.core/load hasn't really changed since ~2009, so monkey patching here
; seems entirely reasonable/safe.
(defn- cljx-load
  "Loads Clojure code from resources in classpath. A path is interpreted as
  classpath-relative if it begins with a slash or relative to the root
  directory for the current namespace otherwise."
  {:added "1.0"}
  [& paths]
  (doseq [^String path paths]
    (let [^String path (if (.startsWith path "/")
                         path
                         (str (#'clojure.core/root-directory (ns-name *ns*)) \/ path))]
      (when @#'clojure.core/*loading-verbosely*
        (printf "(clojure.core/load \"%s\")\n" path)
        (flush))
      (#'clojure.core/check-cyclic-dependency path)
      (when-not (= path (first @#'clojure.core/*pending-paths*))
        (with-bindings {#'clojure.core/*pending-paths* (conj @#'clojure.core/*pending-paths* path)}
          (let [base-resource-path (.substring path 1)
                cljx-path (str base-resource-path ".cljx")]
            (if-let [cljx (find-resource cljx-path)]
              (do
                (when @#'clojure.core/*loading-verbosely*
                  (printf "Transforming cljx => clj from %s.cljx\n" base-resource-path))
                (-> (slurp cljx)
                    (cljx.core/transform cljx.rules/clj-rules)
                    java.io.StringReader.
                    (clojure.lang.Compiler/load cljx-path
                                                (last (re-find #"([^/]+$)" cljx-path)))))
              (clojure.lang.RT/load base-resource-path))))))))

(defonce ^:private clojure-load load)

(defn enable-cljx-load-support!
  []
  (alter-var-root #'load (constantly cljx-load)))

(defn disable-cljx-load-support!
  []
  (alter-var-root #'load (constantly clojure-load)))

(defn cljx-file?
  [file]
  (cond
    (string? file) (boolean (re-find #"\.cljx$" file))
    (instance? File file) (cljx-file? (.getPath file))
    :default false))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn- dir-has-file?
  [dir fname]
  (.exists (io/file (str dir "/" fname))))

(defn- find-project-dir
  [dir]
  (if-not dir
    nil
    (if (or (dir-has-file? dir "project.clj") (dir-has-file? dir "pom.xml"))
      dir
      (find-project-dir (.getParentFile dir)))))

(defn ns-compile-cljx->cljs
  [ns-name f & [project-dir]]
  (if (cljx-file? f)
    (let [f (file f)]
      (if-let [project-dir (or project-dir (find-project-dir (.getParentFile f)))]
        (let [out-file (io/file (str project-dir "/target/classes/"
                                     (ns-name->rel-path ns-name ".cljs")))]
          (-> out-file .getParentFile .mkdirs)
          (spit out-file (.getCljs f)))))))
