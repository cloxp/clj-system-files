(ns rksm.system-files.fs-util-test
  (:refer-clojure :exclude [add-classpath])
  (:require [clojure.test :refer :all]
            [rksm.system-files.fs-util :refer :all]
            [rksm.system-files :refer (classpath-for-ns file-for-ns)]))

(deftest ns-internals

  (testing "get relative path"
    (is (= "baz.txt"
           (path-relative-to "/foo/bar/" "/foo/bar/baz.txt")))

    (is (= "baz/zork.txt"
           (path-relative-to "/foo/bar/" "/foo/bar/baz/zork.txt")))

    (is (= "../baz/zork.txt"
           (path-relative-to "/foo/bar/" "/foo/baz/zork.txt")))

    (is (= "rksm/system_files/fs_util_test.clj"
           (path-relative-to
            (classpath-for-ns 'rksm.system-files.fs-util-test)
            (file-for-ns 'rksm.system-files.fs-util-test)))))
  )

(deftest remove-parent-path-test
  (is (= ["/foo/bar"]
         (remove-parent-paths ["/foo/bar" "/foo"])))
  
  (is (= ["/a/b" "/a/c/d"]
         (remove-parent-paths ["/a/b" "/a/c/d" "/a/c"])))
  )

(comment
 (test-ns 'rksm.system-files.fs-util-test))