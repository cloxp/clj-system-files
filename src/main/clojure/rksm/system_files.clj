(ns rksm.system-files
    (:refer-clojure :exclude [add-classpath])
    (:require [clojure.tools.namespace.find :as nf]
              [clojure.tools.namespace.repl :as nr]
              [clojure.tools.namespace.file :as tn-file]
              [clojure.java.classpath :as cp]
              [clojure.java.io :as io]
              [dynapath.util :as dp]
              [cemerick.pomegranate]
              [rksm.system-files.fs-util :as fs-util]
              [rksm.system-files.jar-util :as jar]
              [clojure.string :as s]
              [rksm.system-files.cljx.File :as cljx-file])
  (:import (java.io File)
           (rksm.system-files.jar.File)
           (rksm.system-files.cljx.File)))

(declare file ns-name->rel-path classpath add-project-dir)

(def jar-url-for-ns jar/jar-url-for-ns)
(def jar-url->reader jar/jar-url->reader)
(def jar-entries-matching jar/jar-entries-matching)
(def jar? jar/jar?)
(def namespaces-in-jar (memoize jar/namespaces-in-jar))

(defn classloaders
  []
  (->> (Thread/currentThread)
       .getContextClassLoader
       (iterate #(.getParent %))
       (take-while boolean)
       (filter dp/addable-classpath?)))

(defn add-classpath
  [cp]
  (if-not (some (partial = (io/file cp)) (classpath))
    (dp/add-classpath-url
     (last (classloaders))
     (-> cp io/file .toURI .toURL))))


(def common-src-dirs ["src/main/clojure", "src/main/clj", "src/main", "src/clojure", "src/clj", "src"])
(def common-cljs-dirs ["src/main/cljs", "src/cljs", "src"])
(def common-test-dirs ["src/test/clojure", "src/test/clj", "src/test", "test/clojure", "test/clj", "test"])
(def common-cljs-test-dirs ["src/test/cljs", "src/test", "test/cljs", "src"])
(def class-dirs ["classes"])

(defn- first-existing-file
  [base-dir paths]
  (->> paths
    (map #(str base-dir java.io.File/separator %))
    (map io/file)
    (filter #(.exists %))
    first))

(comment
 (first-existing-file "/Users/robert/clojure/system-navigator" common-src-dirs)
 (find-source-test-compile-dirs "/Users/robert/clojure/system-navigator"))

(defn find-source-test-compile-dirs
  [base-dir]
  (let [d base-dir
        find-first (partial first-existing-file d)]
    (->> [;(find-first class-dirs)
          (find-first common-src-dirs)
          (find-first common-test-dirs)
          (find-first common-cljs-dirs)
          (find-first common-cljs-test-dirs)]
      (filter boolean)
      fs-util/remove-parent-paths)))

(defn add-common-project-classpath
  [& [base-dir]]
  (add-project-dir
   (or base-dir (System/getProperty "user.dir"))))

(defn classpath-dirs
  []
  (filter #(.isDirectory %) (classpath)))

(defn classpath-of-project
  [project-dir]
  (let [p (.getCanonicalPath (io/file project-dir))]
    (->> (classpath)
      (filter #(.startsWith (str %) p))
      fs-util/remove-parent-paths)))

(defn- classpath-dir-known?
  [dir]
  (->> (classpath-dirs)
    (map str)
    (filter #(re-find (re-pattern dir) %))
    not-empty))

(defn maybe-add-classpath-dir
  [dir]
  (if-not (classpath-dir-known? dir)
    (add-common-project-classpath dir)))

(comment

 (rksm.system-navigator.ns.filemapping/maybe-add-classpath-dir "/Users/robert/clojure/system-navigator")
 (classpath-dir-known? "/Users/robert/clojure/cloxp-trace")
 (map str (classpath-dirs))
 )

; -=-=-=-=-=-=-=-=-
; class path lokup
; -=-=-=-=-=-=-=-=-

(defn system-classpath
  []
  (some->> (System/getProperty "java.class.path")
    io/file
    (#(try (java.util.jar.JarFile. %) (catch Exception _)))
    jar/classpath-from-system-cp-jar))

(defn classpath
  []
  (distinct (concat (system-classpath)
                    (cp/classpath)
                    (->> (dp/all-classpath-urls) (map io/file)))))

(defn sorted-classpath
  "returns classpath entries in an order that will prioritize local source
  directories over maven and compiled jars and  target/classes dirs"
  []
  (->> (classpath)
    (sort-by #(-> % .isDirectory not))
    (sort-by #(-> % .getCanonicalPath (.endsWith "/classes")))))

(defn loaded-namespaces
  [& {m :matching}]
  (let [nss (nf/find-namespaces (sorted-classpath))
        filtered (if m (filter #(re-find m (str %)) nss) nss)]
    (-> filtered distinct sort)))

(comment
  (loaded-namespaces)
  (loaded-namespaces :matching #"rksm")
  )

; (defn classpath-for-ns
;   [ns-name]
;   (let [found (for [cp (sorted-classpath)]
;                 (if (some #{ns-name} (nf/find-namespaces [cp])) cp))]
;     (first (remove nil? found))))

(defn namespaces-in-dir
  [^File dir matcher]
  (binding [cljx-file/*output-mode* :clj]
    (doall
      (->> (fs-util/walk-dirs dir matcher)
        (filter #(.isFile %))
        (map file)
        (keep tn-file/read-file-ns-decl)
        (map second)))))

(defn find-namespaces
  [^File cp ext-matcher]
  (let [ext-matcher (if (string? ext-matcher)
                      (re-pattern ext-matcher)
                      ext-matcher)]
   (cond
     (.isDirectory cp) (namespaces-in-dir cp ext-matcher)
     (jar/jar? cp) (jar/namespaces-in-jar cp ext-matcher)
     :default [])))

(defn classpath-for-ns
  [name-of-ns & [ext]]
  (let [name-of-ns (cond
                     (symbol? name-of-ns) name-of-ns
                     (string? name-of-ns) (symbol name-of-ns)
                     (instance? clojure.lang.Namespace name-of-ns) (ns-name name-of-ns)
                     :default name-of-ns)
        ext (or ext #"\.cljx?$")]
    (let [found (for [cp (sorted-classpath)]
                  (if (some #{name-of-ns} (find-namespaces cp ext)) cp))]
      (first (remove nil? found)))))


; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; classpath / namespace -> files
; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(def ns-name->rel-path fs-util/ns-name->rel-path)
(def rel-path->ns-name fs-util/rel-path->ns-name)

(defn clj-files-in-dir
  [dir & [ext]]
  (let [ext (or ext #"\.cljx?")]
    (->> dir
      (tree-seq #(.isDirectory %) #(.listFiles %))
      (filter #(and (not (.isDirectory %))
                    (re-find ext (.getName %)))))))

(defn file-for-ns
  "tries to find a filename for the given namespace"
  [ns-name & [file-name ext]]
  (if file-name
    (let [f (file file-name)]
      (if (jar/jar? f)
        (file (jar/jar-url-in-jar ns-name f ext))
        f))
    (if-let [cp (classpath-for-ns ns-name ext)]
      (cond
        (.isDirectory cp)
        (let [path-pattern (re-pattern (str (ns-name->rel-path ns-name (or ext ".clj(x|s)?")) "$"))]
          (->> (clj-files-in-dir cp ext)
            (filter #(re-find path-pattern (.getCanonicalPath %)))
            first file))
        (jar/jar? cp) (file-for-ns ns-name cp ext)
        :default cp))))

(defn relative-path-for-ns
  "relative path of ns in regards to its classpath"
  [ns & [file-name]]
  (if-let [fn (file-for-ns ns file-name)]
    (cond
      (jar/jar? fn) (some-> (java.util.jar.JarFile. fn)
                      (jar/jar-entry-for-ns ns) .getName)
      (instance? rksm.system-files.jar.File fn) (-> fn .getJarEntry .getName)
      :default (some-> (classpath-for-ns ns)
                 (fs-util/path-relative-to fn)))))

(defn file-name-for-ns
  [ns]
  (.getCanonicalPath (file-for-ns ns)))

(defn source-reader-for-ns
  [ns-name & [file-name ext]]
  (some-> (file-for-ns ns-name file-name ext) file io/reader))

(defn source-for-ns
  [ns-name & [file-name ext]]
  (some->
    (source-reader-for-ns ns-name file-name ext)
    slurp))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn discover-ns-in-cp-dir
  [dir & [file-match]]
  (let [file-match (or file-match #".*\.cljx?$")
        dir (io/file dir)]
    (find-namespaces dir file-match)))

(defn discover-ns-in-project-dir
  [dir & [file-match]]
  (->> (find-source-test-compile-dirs dir)
    (mapcat #(discover-ns-in-cp-dir % file-match))
    distinct))

(defn add-project-dir
  [dir & [{:keys [source-dirs project-file-match] :or {source-dirs []}}]]
  (doseq [new-cp (concat (find-source-test-compile-dirs dir) source-dirs)]
    (cemerick.pomegranate/add-classpath new-cp))
  (discover-ns-in-project-dir dir project-file-match))

(defn refresh-classpath-dirs
  []
  (apply nr/set-refresh-dirs (classpath-dirs))
  (nr/refresh-all))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; a better all-ns
; -=-=-=-=-=-=-=-=-

(defn- filter-files
  [files file-match]
  (filter
   (fn [f]
     (and
      (not (.startsWith f "META-INF"))
      (not= f "project.clj")
      (re-find file-match f)))
   files))

(defn- find-namespace-data
  [cp & [file-match]]
  (let [file-match (or file-match #"\.cljx?$")
        jar? (boolean (re-find #"\.jar$" (.getName cp)))
        sep java.io.File/separator]
    (if-let [files (cond
                     (not (.exists cp)) nil
                     (.isDirectory cp) (map (partial fs-util/path-relative-to cp)
                                            (clj-files-in-dir cp))
                     jar? (->> cp java.util.jar.JarFile. .entries iterator-seq (map #(.getName %)))
                     :default nil)]
      (map (fn [rel-path]
             {:jar? jar?
              :cp cp
              :ns (rel-path->ns-name rel-path)
              :file (if jar? (str "jar:file:" cp "!" sep rel-path) (str cp sep rel-path))})
           (filter-files files file-match)))))

; (defn find-namespaces-on-cp
;   [& [file-match]]
;   (->> (sorted-classpath)
;     (mapcat #(find-namespace-data % file-match))
;     (map :ns)
;     distinct sort))

(defn find-namespaces-on-cp
  [& [file-match]]
  (let [file-match (or file-match #"\.cljx?$")]
    (->> (sorted-classpath)
      (mapcat #(find-namespaces % file-match))
      distinct sort)))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn ensure-file
  [& [file ext]]
  (if file
    (let [f (clojure.java.io/file file)]
      (.mkdirs (.getParentFile f))
      (.createNewFile f)
      f)
    (let [name (str "file-less-namespace_"
                    (quot (System/currentTimeMillis) 1000))
          f (java.io.File/createTempFile name (or ext ".clj"))]
      f)))

(defn ensure-classpath-for-new-ns
  [ns-name dir]
  (if-not (->> (classpath)
            (map #(.getCanonicalPath %))
            (some #{dir}))
    (add-classpath dir)))

(defn create-namespace-file
  [ns-name dir ext]
  (let [path (ns-name->rel-path ns-name ext)
        fname (str dir java.io.File/separator path)
        f (ensure-file fname ext)]
    (spit f (format "(ns %s)" ns-name))
    (ensure-classpath-for-new-ns ns-name dir)
    (if (or (= ext ".clj") (= ext ".cljx"))
      (require ns-name :reload))
    (.getAbsolutePath f)))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn- make-cljx-file
  [file-name]
  (rksm.system-files.cljx.File. file-name))

(defn- make-jar-file
  [file-name]
  (rksm.system-files.jar.File. file-name))

(defn file
  [file]
  (if-not file
    nil
    (let [is-file? (instance? File file)
          is-cljx-file? (and is-file? (instance? rksm.system-files.cljx.File file))
          is-jar-file? (and is-file? (instance? rksm.system-files.jar.File file))]
      (if (or is-jar-file? is-cljx-file?)
        file
        (let [file-name (cond
                          (string? file) file
                          is-file? (.getCanonicalPath file)
                          :default (str file))]
          (cond
            (jar/jar-url-string? file-name) (make-jar-file file-name)
            (re-find #"^file:" file-name) (recur (s/replace file-name #"^file:" ""))
            (re-find #"\.jar!/" file-name) (let [[_ path in-jar-path] (re-find #"([^!]+)!/(.*)" file-name)
                                                 abs-path (.getCanonicalPath (io/file path))]
                                             (make-jar-file (str abs-path "!/" in-jar-path)))
            (re-find #"\.clj$" file-name) (if is-file? file (io/file file-name))
            (re-find #"\.cljx$" file-name) (make-cljx-file file-name)
            :default (io/file file-name)))))))

(comment
 (slurp (file "project.clj"))
 (.getCanonicalPath (file "test-resources/dummy-2-test.jar"))

 (slurp (file "test-resources/dummy-2-test.jar!/rksm/system_files/test/dummy_2.clj"))
 (slurp (file "test-resources/dummy-2-test.jar!/rksm/system_files/test/dummy_2.clj"))

  (classpath)
  (loaded-namespaces)
  (file-for-ns 'rksm.system-files)
  (file-for-ns 'rksm.system-files "/Users/robert/clojure/system-files/src/main/clojure/rksm/system_files.clj")
  (relative-path-for-ns 'rksm.system-files "/Users/robert/clojure/system-files/src/main/clojure/rksm/system_navigator.clj")
  (refresh-classpath-dirs))