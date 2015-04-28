(ns ^{:doc "File abstraction that points to a jar entry. More convenient to use
than with specific jar / jar entry handling. Also, interfaces that expect
dealing with files (like cljs.analyzer) can transparently use the jar.File
without creating temp files."}
  rksm.system-files.jar.File
  (:require [clojure.java.io :as io]
            [rksm.system-files.jar-util :as jar]
            [clojure.string :as string])
  (:import (java.io File FileInputStream InvalidObjectException)
           (java.net URL)
           (java.util.jar JarFile JarEntry))
  (:gen-class :name rksm.system-files.jar.File
              :extends java.io.File
              :main false
              :init init
              :state paths
              :constructors {[String] [String]
                             [String String] [String]}
              :methods [[getJarURLString [] String]
                        [getJar [] java.util.jar.JarFile]
                        [getJarEntry [] java.util.jar.JarEntry]]
              :exposes-methods {exists super-exists}))

(defn -init
  ([path]
   (let [[full-path _ _ file-path _ path-in-jar] (re-find #"(jar:)?(file:)?([^!]+)!(/|\\)(.*)" path)]
     (if-not path-in-jar (throw (InvalidObjectException. (str "Cannot extract path-in-jar in " path))))
     (if-not file-path (throw (InvalidObjectException. (str "Cannot extract file path in " path))))
     (-init file-path path-in-jar)))
  ([file-path path-in-jar]
   (let [full-path (str "jar:file:" file-path "!/" path-in-jar)]
     [[full-path] {:path-in-jar path-in-jar, :path-to-file file-path}])))

(defn -getJar
  [this]
  (java.util.jar.JarFile. (-> this .paths :path-to-file)))

(defn -getJarEntry
  [this]
  (first 
   (jar/jar-entries-matching
    (.getJar this)
    (re-pattern (str "^"
                     (string/replace
                      (-> this .paths :path-in-jar)
                      #"\\" ".")
                     "$")))))

(defn -getJarURLString
  [this]
  (str "jar:file:" (-> this .paths :path-to-file)
       "!/" (-> this .paths :path-in-jar)))

(defn -exists
  [this]
  (and (.exists (File. (-> this .paths :path-to-file)))
      (boolean (.getJarEntry this))))

(defn -canWrite
  [this]
  false)

(defn -canRead
  [this]
  (.exists this))

(defn -toURL
  [this]
  (java.net.URL. (.getJarURLString this)))

(defn -getAbsoluteFile
  [this]
  (rksm.system-files.jar.File.
   (.getAbsolutePath (File. (-> this .paths :path-to-file)))
   (-> this .paths :path-in-jar)))

(defn -getAbsolutePath
  [this]
  (.getPath (.getAbsoluteFile this)))

(defn -getCanonicalFile
  [this]
  (rksm.system-files.jar.File.
   (.getCanonicalPath (File. (-> this .paths :path-to-file)))
   (-> this .paths :path-in-jar)))

(defn -getCanonicalPath
  [this]
  (.getPath (.getCanonicalFile this)))

(extend rksm.system-files.jar.File
  io/IOFactory
  (assoc io/default-streams-impl
         :make-input-stream
         (fn [^rksm.system-files.jar.File file opts]
           (jar/jar+entry->reader (.getJar file) (.getJarEntry file)))))
 