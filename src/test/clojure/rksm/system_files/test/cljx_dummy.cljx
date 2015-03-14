(ns rksm.system-files.test.cljx-dummy)

(def x 23)

(defonce dummy-atom (atom []))

(defn test-func
  [y]
  (swap! dummy-atom conj (+ x y)))

(defmacro foo
  [x & body]
  `(foo ~x ~@body))
