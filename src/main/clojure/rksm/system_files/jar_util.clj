(ns rksm.system-files.jar-util
  (:require [clojure.java.io :as io]
            [clojure.tools.namespace.find :as tn-find]
            [rksm.system-files.fs-util :as fs-util])
  (:import (java.io File)))

; -=-=-=-=-=-=-
; jar related
; -=-=-=-=-=-=-

(defn jar+entry->reader
  [jar entry]
  (io/reader (.getInputStream jar entry)))

(defn jar-url-string?
  [jar-url]
  (boolean (and
            (string? jar-url)
            (re-find #"^(jar:)?file:([^!]+)!\/?.*" jar-url))))

(defn jar-url->reader
  "expects a jar-url String that identifies a jar and an entry in it, like
  jar:file:/foo/.m2/repository/org/xxx/bar/0.1.2/bar.jar!/my/ns.cljs"
  [^String jar-url]
  (if-let [jar-match (re-find #"^(jar:)?file:([^!]+)!\/?(.*(\.clj(s|x)?))" jar-url)]
    (let [[_ _ jar-path jar-entry-path ext] jar-match
          jar (java.util.jar.JarFile. jar-path)
          entry (.getEntry jar jar-entry-path)]
      (jar+entry->reader jar entry))))

(defn jar-url-for-ns
  [ns-name & [ext]]
  (some-> ns-name
    (fs-util/ns-name->rel-path ext)
    ClassLoader/getSystemResource
    .toString))

(defn jar-entries-matching
  [jar-file matcher]
  (->> jar-file .entries
    iterator-seq
    (filter #(re-find matcher (.getName %)))))

(defn jar-entry-for-ns
  [jar-file ns-name & [ext]]
  (let [ext (or ext ".clj(x|s)?$")
        rel-name (fs-util/ns-name->rel-path ns-name ext)
        pat (re-pattern rel-name)]
    (first (jar-entries-matching jar-file pat))))

(defn jar-url-in-jar
  [ns-name ^java.io.File jar-file & [ext-match]]
  (if-let [entry (jar-entry-for-ns (java.util.jar.JarFile. jar-file) ns-name ext-match)]
    (str "jar:file:" (.getCanonicalPath jar-file) "!/" entry)))

(comment
 (jar-url->entry "jar:file:/Users/robert/.m2/repository/org/clojure/core.async/0.1.346.0-17112a-alpha/core.async-0.1.346.0-17112a-alpha.jar!/cljs/core/async.cljs")
 )

(def ^{:doc "returns seq if {:file STRING, :ns SYMBOL, :decl FORM}"}
  namespaces-in-jar
  (memoize
   (fn
     [^File jar-file matcher]
     (let [jar (java.util.jar.JarFile. jar-file)
           jar-entries (map #(.getName %) (jar-entries-matching jar matcher))]
       (let [jar (java.util.jar.JarFile. jar-file)
             jar-entries (map #(.getName %) (jar-entries-matching jar matcher))]
         (->> jar-entries
           (map (juxt identity (partial tn-find/read-ns-decl-from-jarfile-entry jar)))
           (keep (fn [[file decl]] (if decl {:file file :ns (second decl) :decl decl})))))))))

(defn classpath-from-system-cp-jar
  [jar-file]
  (some->> jar-file
           .getManifest
           .getMainAttributes
           (filter #(= "Class-Path" (-> % key str)))
           first .getValue
           (#(clojure.string/split % #" "))
           (map #(java.net.URL. %))
           (map io/as-file)))

(defn jar?
  [f]
  (boolean
   (and
    (instance? java.io.File f)
    (.exists f)
    (not (.isDirectory f))
    (try
      (java.util.jar.JarFile. f)
      (catch Exception e false)))))

(defn namespaces-in-jar
  [^File jar-file matcher]
  (let [jar (java.util.jar.JarFile. jar-file)
        jar-entries (map #(.getName %) (jar-entries-matching jar matcher))]
    (let [jar (java.util.jar.JarFile. jar-file)
          jar-entries (map #(.getName %) (jar-entries-matching jar matcher))]
      (->> jar-entries
        (map (juxt identity (partial tn-find/read-ns-decl-from-jarfile-entry jar)))
        (keep (fn [[file decl]] (if decl {:file file :ns (second decl) :decl decl})))))))