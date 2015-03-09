(ns rksm.system-files.fs-util
  (:require
   [clojure.java.io :as io]
   [clojure.string :as string]
   [clojure.java.io :as io])
  (:import java.io.File))

(defn walk-dirs [dirpath pattern]
  (doall (filter #(re-find pattern (.getName %))
                 (file-seq (io/file dirpath)))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; borrowoed from https://github.com/clojure/clojurescript/blob/master/src/clj/cljs/closure.clj

(defn path-seq
  [file-str]
  (->> File/separator
    java.util.regex.Pattern/quote
    re-pattern
    (string/split file-str)))

(defn to-path
  ([parts]
     (to-path parts File/separator))
  ([parts sep]
    (apply str (interpose sep parts))))

(defn path-relative-to
  "Generate a string which is the path to input relative to base."
  [^File base input]
  (let [base-path (path-seq (.getCanonicalPath (io/file base)))
        input-path (path-seq (.getCanonicalPath (io/file input)))
        count-base (count base-path)
        common (count (take-while true? (map #(= %1 %2) base-path input-path)))
        prefix (repeat (- count-base common) "..")]

    ; (println base-path input-path common count-base)
    (if (= count-base common)
      (to-path (drop common input-path) "/")
      (to-path (concat prefix (drop common input-path)) "/")
      )))

(defn parent?
  [maybe-parent-dir dir]
  (let [sep java.io.File/separator
        a (.getCanonicalPath (io/file maybe-parent-dir))
        b (.getCanonicalPath (io/file dir))]
    (and (not= a b) (.startsWith b (str a sep)))))

(defn remove-parent-paths
  "if dirs is [/foo/bar /foo], remove /foo"
  [dirs]
  (filter (fn [dir] (every? #(or (= dir %) (not (parent? dir %))) dirs)) dirs))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn set-cwd!
  [dir]
  (let [dir (io/file dir)
        path (.getCanonicalPath dir)]
    (if-not (.exists dir)
      (throw (Exception. (str "Directory " dir " does not exist!")))
      (do 
        (System/setProperty "user.dir" path)
        path))))

(comment
 (require '[rksm.system-navigator.ns.filemapping :refer (classpath-for-ns file-for-ns)])

 (-> (classpath-for-ns 'rksm.system-navigator.ns.internals) io/file .getCanonicalPath path-seq count)
 (-> (file-for-ns 'rksm.system-navigator.ns.internals) io/file .getCanonicalPath path-seq count)

 (path-relative-to (classpath-for-ns 'rksm.system-navigator.ns.internals)
                   (file-for-ns 'rksm.system-navigator.ns.internals))

 )