(ns rksm.system-files.cljx.File
  (:require [clojure.java.io :as io]
            (cljx core rules))
  (:import (java.io File FileInputStream))
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
                        [getMode [] clojure.lang.Keyword]]))

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

(def output-modes [:cljx :cljs :clj])

(def ^:dynamic *output-mode* nil)

(defn output-mode
  [& [^rksm.system-files.cljx.File file]]
  (or (some-> file .getMode) *output-mode* :cljx))

(extend rksm.system-files.cljx.File
  io/IOFactory
  (assoc io/default-streams-impl
         :make-input-stream
         (fn [^rksm.system-files.cljx.File file opts]
           (let [mode (output-mode file)
                 rules (case mode
                         :cljx nil
                         :clj cljx.rules/clj-rules
                         :cljs cljx.rules/cljs-rules
                         mode)]
             (if-not rules
               (io/make-input-stream (FileInputStream. file) opts)
               (-> (.getCanonicalPath file)
                 slurp (cljx.core/transform rules)
                 java.io.StringBufferInputStream.
                 io/input-stream))))))

(defn with-mode
  [^String path ^clojure.lang.Keyword mode]
  (doto
    (rksm.system-files.cljx.File. path)
    (.changeMode mode)))
