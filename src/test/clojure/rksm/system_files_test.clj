(ns rksm.system-files-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-files :refer :all]
            [rksm.system-files.jar-util :as jar]
            [clojure.java.io :as io]))

(def jar-test-file (clojure.java.io/file "test-resources/dummy-2-test.jar"))

(deftest system-files

  (add-classpath "test-resources/dummy-2-test.jar")

  (require 'rksm.system-files.test.dummy-1)
  (require 'rksm.system-files.test.dummy-2)
  (require 'rksm.system-files.test.dummy-3)

  (testing "find loaded namespaces"
    (is (= ['rksm.system-files.test.dummy-1
            'rksm.system-files.test.dummy-2 
            'rksm.system-files.test.dummy-3]
           (loaded-namespaces :matching #"rksm.system-files.*dummy-[0-9]$"))))

  (testing "namespace to classpath mapping"
    (testing "for dirs"
      
      (is
        (->> (classpath-for-ns 'rksm.system-files.test.dummy-1)
             str
             (re-find #"src/test/clojure$")))
      (is
        (->> (file-for-ns 'rksm.system-files.test.dummy-1)
             str
             (re-find #"src/test/clojure/rksm/system_files/test/dummy_1.clj$")))))

    (testing "for jars"
      (is
        (->> (classpath-for-ns 'rksm.system-files.test.dummy-2)
             str
             (re-find #"test-resources/dummy-2-test.jar$"))))

  (testing "map namespaces to sources"
    (testing "for plain clj files"
      (is (= "(ns rksm.system-files.test.dummy-1)\n\n(def x 23)\n"
              (source-for-ns 'rksm.system-files.test.dummy-1))))
    (testing "for jars"
      (is (= "(ns rksm.system-files.test.dummy-2\n    (:gen-class))\n\n(def y 24)\n"
              (source-for-ns 'rksm.system-files.test.dummy-2)))))
  
  (testing "relative namespace paths"
    (is (= "rksm/system_files/test/dummy_1.clj"
           (relative-path-for-ns 'rksm.system-files.test.dummy-1)))
    (is (= "rksm/system_files/test/dummy_2.clj"
           (relative-path-for-ns 'rksm.system-files.test.dummy-2))))
  )

(deftest find-jar-url
  (is (= (str "jar:file:"
              (.getCanonicalPath jar-test-file)
              "!/rksm/system_files/test/dummy_2.clj") 
         (jar/jar-url-for-ns 'rksm.system-files.test.dummy-2))))

(deftest read-namespace-in-jar
  (is (= "(ns rksm.system-files.test.dummy-2\n    (:gen-class))\n\n(def y 24)\n"
         (rksm.system-files/source-for-ns 'rksm.system-files.test.dummy-2))))

(deftest read-jar-url-file
  (is (= "(ns rksm.system-files.test.dummy-2\n    (:gen-class))\n\n(def y 24)\n"
         (slurp
          (rksm.system-files.jar.File.
           (str "jar:file:"
                (.getCanonicalPath jar-test-file)
                "!/rksm/system_files/test/dummy_2.clj"))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
  (run-tests 'rksm.system-files-test)
  
 (.getName(relative-path-for-ns 'rksm.system-files.test.dummy-2))
  (require 'rksm.system-files.test.dummy-2)
  (classpath-for-ns 'rksm.system-files.test.dummy-2)
  (source-for-ns 'clojure.core)
  (file-for-ns 'rksm.system-files.test.dummy-2)
  (file-for-ns 'rksm.system-files.test.dummy-1)
  )