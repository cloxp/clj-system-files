(defproject org.rksm/system-files "0.1.3-SNAPSHOT"
  :description "Accessing clojure classpath data and system files."
  :license "MIT"
  :url "http://github.com/cloxp/cloxp-projects"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.namespace "0.2.8"]
                 [org.clojure/java.classpath "0.2.2"]
                 [org.tcrawley/dynapath "0.2.3"]
                 [com.cemerick/pomegranate "0.3.0"]]
  :source-paths ["src/main/clojure"]
  :test-paths ["src/main/test"]
  :scm {:url "git@github.com:cloxp/system-files.git"}
  :pom-addition [:developers [:developer
                              [:name "Robert Krahn"]
                              [:url "http://robert.kra.hn"]
                              [:email "robert.krahn@gmail.com"]
                              [:timezone "-9"]]])