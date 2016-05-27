(ns cambium.mdc-test
  (:require
    [clojure.test :refer :all]
    [cambium.core        :as c]
    [cambium.mdc         :as m]
    [cambium.test-util   :as tu]))


(deftest test-mdc
  (let [context-old {"foo" "bar"
                     "baz" "quux"}
        context-new {"foo" "10"
                     "bar" "baz"}
        f (fn
            ([]
              (is (= "bar"  (c/context-val "foo")))
              (is (= "quux" (c/context-val "baz")))
              (is (nil? (c/context-val "bar"))))
            ([dummy arg]))]
    (testing "with-raw-mdc"
      (is (nil? (c/context-val "foo")) "Attribute not set must be absent before override")
      (m/with-raw-mdc context-old
        (f)
        (m/with-raw-mdc context-new
          (is (= "10" (c/context-val "foo")))
          (is (nil? (c/context-val "baz")) "Wholesale MDC replacement must remove non-overridden attributes")
          (is (= "baz" (c/context-val "bar")))))
      (is (nil? (c/context-val "foo")) "Attribute not set must be absent after restoration"))
    (testing "wrap-raw-mdc"
      (is (nil? (c/context-val "foo")))
      ((m/wrap-raw-mdc context-old f))
      ((m/wrap-raw-mdc context-old f) :dummy :arg)
      ((comp (partial m/wrap-raw-mdc context-new) (m/wrap-raw-mdc context-old f)))
      (is (nil? (c/context-val "foo"))))))
