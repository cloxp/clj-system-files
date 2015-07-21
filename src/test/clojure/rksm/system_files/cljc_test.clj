(ns rksm.system-files.cljc-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]))

(defn fixture [test]
  (test)
  (remove-ns 'rksm.system-files.test.cljc-dummy))

(use-fixtures :each fixture)

(deftest cljc-can-be-required
  (require 'rksm.system-files.test.cljc-dummy :reload)
  (is (= #{'y 'x-to-string 'x}
         (-> 'rksm.system-files.test.cljc-dummy ns-interns keys set)))
  (is (= 23
         (eval 'rksm.system-files.test.cljc-dummy/x)))
  (is (= "rksm/system_files/test/cljc_dummy.cljc"
         (-> 'rksm.system-files.test.cljc-dummy/x
           find-var meta :file))))

; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(comment
 (let [s (java.io.StringWriter.)]
    (binding [*test-out* s]
      (run-tests *ns*)
      (print (str s))))
 )
