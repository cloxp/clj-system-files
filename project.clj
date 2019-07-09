(defproject org.rksm/system-files "0.2.1-SNAPSHOT"
  :description "Accessing clojure classpath data and system files."
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}
  :url "http://github.com/cloxp/cloxp-projects"
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.namespace "0.3.0"]
                 [org.clojure/java.classpath "0.3.0"]
                 [org.tcrawley/dynapath "1.0.0"]
                 [com.cemerick/pomegranate "1.1.0"]]
  :profiles {:dev {:source-paths ["test-resources/dummy-2-test.jar"]}}
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :test-selectors {:default (fn [m] (do (:test m)))}
  :aot [rksm.system-files.jar.File]
  :scm {:url "git@github.com:cloxp/system-files.git"}
  :deploy-repositories [["releases" :clojars]
                        ["snapshots" :clojars]]
  :pom-addition [:developers [:developer [:name "Robert Krahn"]]])
