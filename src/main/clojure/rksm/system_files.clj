(ns rksm.system-files
    (:refer-clojure :exclude [add-classpath])
    (:require [clojure.tools.namespace.find :as nf]
              [clojure.tools.namespace.repl :as nr]
              [clojure.java.classpath :as cp]
              [clojure.java.io :as io]
              [dynapath.util :as dp]
              [cemerick.pomegranate]
              [rksm.system-files.fs-util :as fs]
              [clojure.string :as s])
  (:import [java.io File]))

(declare ns-name->rel-path classpath add-project-dir)

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
      fs/remove-parent-paths)))

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
      fs/remove-parent-paths)))

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

; -=-=-=-=-=-=-
; jar related
; -=-=-=-=-=-=-

(defn jar+entry->reader
  [jar entry]
  (io/reader (.getInputStream jar entry)))

(defn jar-clojure-url-string?
  [jar-url]
  (boolean (and
            (string? jar-url)
            (re-find #"^(jar:)?file:([^!]+)!\/?(.*(\.clj(s|x)?))" jar-url))))

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
    (ns-name->rel-path ext)
    ClassLoader/getSystemResource
    .toString))

(comment
 (jar-url->entry "jar:file:/Users/robert/.m2/repository/org/clojure/core.async/0.1.346.0-17112a-alpha/core.async-0.1.346.0-17112a-alpha.jar!/cljs/core/async.cljs")
 )

(defn jar-entries-matching
  [jar-file matcher]
  (->> jar-file .entries
    iterator-seq
    (filter #(re-find matcher (.getName %)))))

(defn jar-entry-for-ns
  [jar-file ns-name & [ext]]
  (let [ext (or ext ".clj(x|s)?")
        rel-name (rksm.system-files/ns-name->rel-path ns-name ext)
        pat (re-pattern rel-name)]
    (first (jar-entries-matching jar-file pat))))

(defn jar-reader-for-ns
  [class-path-file ns-name & [ext]]
  (let [jar (java.util.jar.JarFile. class-path-file)
        jar-entry (jar-entry-for-ns jar ns-name ext)]
    (-> jar (.getInputStream jar-entry) io/reader)))

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
  (and
    (.exists f)
    (not (.isDirectory f))
    (try
      (java.util.jar.JarFile. f)
      (catch Exception e false))))

; -=-=-=-=-=-=-=-=-
; class path lokup
; -=-=-=-=-=-=-=-=-

(defn system-classpath
  []
  (some->> (System/getProperty "java.class.path")
    io/file
    (#(try (java.util.jar.JarFile. %) (catch Exception _)))
    classpath-from-system-cp-jar))

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
  (->> (fs/walk-dirs dir matcher)
    (filter #(.isFile %))
    (keep clojure.tools.namespace.file/read-file-ns-decl)
    (map second)))

(def namespaces-in-jar
  (memoize
   (fn [^File jar-file matcher]
     (let [jar (java.util.jar.JarFile. jar-file)
           jar-entries (map #(.getName %) (jar-entries-matching jar matcher))]
       (->> jar-entries
         (keep #(clojure.tools.namespace.find/read-ns-decl-from-jarfile-entry jar %))
         (map second))))))

(defn find-namespaces
  [^File cp ext-matcher]
  (let [ext-matcher (if (string? ext-matcher)
                      (re-pattern ext-matcher)
                      ext-matcher)]
   (cond
     (.isDirectory cp) (namespaces-in-dir cp ext-matcher)
     (jar? cp) (namespaces-in-jar cp ext-matcher)
     :default [])))

(defn classpath-for-ns
  [ns-name & [ext]]
  (let [ext (or ext #"\.cljx?$")]
    (let [found (for [cp (sorted-classpath)]
                  (if (some #{ns-name} (find-namespaces cp ext)) cp))]
      (first (remove nil? found)))))


; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; classpath / namespace -> files
; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn ns-name->rel-path
  [ns-name & [ext]]
  (-> ns-name str
    (clojure.string/replace #"\." "/")
    (clojure.string/replace #"-" "_")
    (str (or ext ".clj"))))

(defn rel-path->ns-name
  [rel-path]
  (-> rel-path
    str
    (clojure.string/replace #"/" ".")
    (clojure.string/replace #"_" "-")
    (clojure.string/replace #".clj(s|x)?$" "")
    symbol))

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
    (io/file file-name)
    (if-let [cp (classpath-for-ns ns-name ext)]
      (if (.isDirectory cp)
        (->> (clj-files-in-dir cp ext)
          (filter #(re-find
                    (re-pattern (str (ns-name->rel-path ns-name (or ext ".clj(x|s)?")) "$"))
                    (.getAbsolutePath %)))
          first)
        cp))))

(defn relative-path-for-ns
  "relative path of ns in regards to its classpath"
  [ns & [file-name]]
  (if-let [fn (file-for-ns ns file-name)]
    (if (jar? fn)
      (some-> (java.util.jar.JarFile. fn)
        (jar-entry-for-ns ns)
        (.getName))
      (some-> (classpath-for-ns ns)
        (fs/path-relative-to fn)))))

(defn file-name-for-ns
  [ns]
  (.getCanonicalPath (file-for-ns ns)))

(defn source-reader-for-ns
  [ns-name & [file-name ext]]
  (if (jar-clojure-url-string? file-name)
    (jar-url->reader file-name)
    (if-let [file (some-> (or file-name (file-for-ns ns-name file-name ext)) io/file)]
      (cond
        (jar? file) (jar-reader-for-ns file ns-name ext)
        file (io/reader file)
        :default nil))))

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
                     (.isDirectory cp) (map (partial fs/path-relative-to cp)
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
    (require ns-name :reload)
    (.getAbsolutePath f)))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn updated-source
  "Takes the new source for a def and produces a new version of the ns source,
  with the new def code embedded. meta-info is a meta-data like structure."
  [sym {:keys [line] :as meta-info} new-src-for-def old-src-for-def file-src]
  (let [lines (s/split-lines file-src)
        line (dec line)
        before-lines (take line lines)
        after-lines (-> old-src-for-def
                      s/split-lines count
                      (drop (drop line lines)))]
    (str (s/join "\n" (concat before-lines [new-src-for-def] after-lines)))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (classpath)
  (loaded-namespaces)
  (file-for-ns 'rksm.system-files)
  (file-for-ns 'rksm.system-files "/Users/robert/clojure/system-files/src/main/clojure/rksm/system_files.clj")
  (relative-path-for-ns 'rksm.system-files "/Users/robert/clojure/system-files/src/main/clojure/rksm/system_navigator.clj")
  (refresh-classpath-dirs)
  )