(ns rksm.system-files.cljx.File
  (:require [clojure.java.io :as io]
            (cljx core rules))
  (:import (java.io File FileInputStream FileOutputStream))
  (:gen-class :name rksm.system-files.cljx.File
              :extends java.io.File
              :main false
              :state mode
              :init init
              :constructors {[String String] [String String]
                             [String] [String]
                             [java.io.File String] [java.io.File String]
                             [java.net.URI] [java.net.URI]}
              :methods [[changeMode [clojure.lang.Keyword] clojure.lang.Keyword]
                        [getMode [] clojure.lang.Keyword]
                        [getCljs [] String]
                        [getClj [] String]]))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
; cljx file related

(defn -init
  ([x]
   [[x] (atom nil)])
  ([x y]
   [[x y] (atom nil)]))

(defn -changeMode
  [this new-mode]
  (reset! (.mode this) new-mode)
  new-mode)

(defn -getMode
  [this]
  (some-> (.mode this) deref))

(defn- input-stream-with-rule
  [^rksm.system-files.cljx.File cljx-file cljx-rule & [opts]]
  (let [rules (case cljx-rule
                :cljx nil
                :clj cljx.rules/clj-rules
                :cljs cljx.rules/cljs-rules
                cljx-rule)]
    (if-not rules
      (io/make-input-stream (FileInputStream. cljx-file) opts)
      (-> (.getCanonicalPath cljx-file)
        slurp (cljx.core/transform rules)
        java.io.StringBufferInputStream.
        io/input-stream))))

(defn -getClj
  [this]
  (slurp (input-stream-with-rule this :clj)))

(defn -getCljs
  [this]
  (slurp (input-stream-with-rule this :cljs)))

(def output-modes [:cljx :cljs :clj])

(def ^:dynamic *output-mode* nil)

(defn output-mode
  [& [^rksm.system-files.cljx.File file]]
  (or *output-mode* (some-> file .getMode) :cljx))

(extend rksm.system-files.cljx.File
  io/IOFactory
  (assoc io/default-streams-impl
         :make-input-stream
         (fn [^rksm.system-files.cljx.File file opts]
           (input-stream-with-rule file (output-mode file) opts))
         :make-output-stream (fn [^rksm.system-files.cljx.File x opts]
                               (io/make-output-stream (io/file (.getPath x)) opts))))

(defn with-mode
  [^String path ^clojure.lang.Keyword mode]
  (doto
    (rksm.system-files.cljx.File. path)
    (.changeMode mode)))
