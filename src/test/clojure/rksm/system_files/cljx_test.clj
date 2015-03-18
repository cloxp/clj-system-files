(ns rksm.system-files.cljx-test
  (:require [clojure.test :refer :all]
            [rksm.system-files.cljx :as cljx]
            [rksm.system-files.loading :as load]))

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

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
 (test-ns 'rksm.system-files.cljx-test)
 
 )