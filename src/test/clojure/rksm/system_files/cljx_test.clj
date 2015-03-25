(ns rksm.system-files.cljx-test
  (:require [clojure.test :refer :all]
            (cljx core rules)
            [rksm.system-files.cljx :as cljx]
            [rksm.system-files.cljx.File :as cljx-file]
            [rksm.system-files.loading :as load]
            [clojure.java.io :as io]))

(defn fixture [test]
  (test)
  (remove-ns 'rksm.system-files.test.cljx-dummy)
  (cljx/enable-cljx-load-support!))

(use-fixtures :each fixture)

(deftest cljx-can-be-required
  (cljx/enable-cljx-load-support!)
  (require 'rksm.system-files.test.cljx-dummy :reload)
  (is (= '(y x-to-string x) (keys (ns-interns 'rksm.system-files.test.cljx-dummy))))
  (is (= 23 (eval 'rksm.system-files.test.cljx-dummy/x)))
  (is (= "rksm/system_files/test/cljx_dummy.cljx"
         (-> 'rksm.system-files.test.cljx-dummy/x
           find-var meta :file)))
  (remove-ns 'rksm.system-files.test.cljx-dummy)
  (cljx/disable-cljx-load-support!)
  (is (thrown-with-msg? java.io.FileNotFoundException #"Could not locate"
                        (require 'rksm.system-files.test.cljx-dummy :reload))))

(deftest cljx-file-reading

  (let [path (str (.getParentFile (rksm.system-files/file-for-ns 'rksm.system-files.cljx-test)) "/test/cljx_dummy.cljx")
        real-content (slurp (io/file path))
        clj-content (cljx.core/transform real-content cljx.rules/clj-rules)
        cljs-content (cljx.core/transform real-content cljx.rules/cljs-rules)
        file (rksm.system-files.cljx.File. path)
        read-normal (slurp file)
        read-clj (binding [cljx-file/*output-mode* :clj] (slurp file))
        read-cljs (binding [cljx-file/*output-mode* :cljs] (slurp file))]

    (testing "output mode binding"
      (is (= real-content read-normal))
      (is (= clj-content read-clj))
      (is (= cljs-content read-cljs)))

    (testing "changeMode"
      (.changeMode file :clj) (is (= clj-content (slurp file)))
      (is (= cljs-content (slurp (cljx-file/with-mode path :cljs)))))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
 (test-ns 'rksm.system-files.cljx-test))
