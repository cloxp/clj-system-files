(ns rksm.system-files.cljx-test
  (:require [clojure.test :refer :all]
            [rksm.system-files.cljx :as cljx]
            [rksm.system-files.loading :as load]))

(deftest load-cljx-namespace-in-clj
  (load/require-ns 'rksm.system-files.test.cljx-dummy)
;   (cljx/require-ns 'rksm.system-files.test.cljx-dummy)
  (is (= 23 (eval 'rksm.system-files.test.cljx-dummy/x)))
  (is (= "rksm/system_files/test/cljx_dummy.cljx"
         (-> 'rksm.system-files.test.cljx-dummy/x
           find-var meta :file))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
 (test-ns 'rksm.system-files.cljx-test)
 
 )