;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns cambium.nested-test
  (:require
    [clojure.test :refer :all]
    [cambium.core   :as c]
    [cambium.nested :as n]
    [cambium.test-util :as tu]))


(deftest log-test
  (testing "Normal scenarios"
    (n/info "hello")
    (n/info {:foo "bar" :baz 10 :qux true} "hello with context")
    (n/with-logging-context {:extra "context" "data" [1 2 :three 'four]}
      (is (= (c/get-context) {"extra" "context" "data" "[1 2 :three four]"}))
      (is (= (n/context-val :extra) "context"))
      (is (nil? (n/context-val "foo")))
      (n/info {:foo "bar"} "hello with wrapped context"))
    (n/error {} (ex-info "some error" {:data :foo}) "internal error"))
  (testing "custom loggers"
    (tu/metrics {:latency-ns 430 :module "registration"} "op.latency")
    (tu/metrics {:module "registration"} (ex-info "some error" {:data :foo}) "internal error")
    (tu/txn-metrics {:module "order-fetch"} "Fetched order #4568"))
  (testing "type-safe encoding"
    (let [sk c/stringify-key]
      (with-redefs [c/stringify-key (fn ^String [x] (.replace ^String (sk x) \- \_))
                    c/stringify-val c/encode-val
                    c/destringify-val c/decode-val]
        (n/info "hello")
        (n/info {:foo-k "bar" :baz 10 :qux true} "hello with context")
        (n/with-logging-context {:extra-k "context" "some-data" [1 2 :three 'four]}
          (is (= (c/get-context) {"extra_k" "context" "some_data" [1 2 :three 'four]}))
          (is (= (n/context-val :extra-k) "context"))
          (is (nil? (n/context-val "foo")))
          (n/info {:foo "bar"} "hello with wrapped context"))
        (n/error {} (ex-info "some error" {:data :foo}) "internal error")))))


(deftest test-context-propagation
  (let [context-old {:foo :bar
                     :baz :quux}
        context-new {:foo 10
                     :bar :baz}
        nested-diff {[:foo-fighter :learn-to-fly] {:title "learn to fly"
                                                   :year 1999}
                     [:foo-fighter :best-of-you ] {:title "best of you"
                                                   :year 2005}}
        f (fn
            ([]
              (is (= "bar"  (n/context-val :foo)))
              (is (= "quux" (n/context-val :baz)))
              (is (nil? (n/context-val :bar))))
            ([dummy arg]))]
    (testing "with-raw-mdc"
      (is (nil? (n/context-val :foo)) "Attribute not set must be absent before override")
      (n/with-logging-context context-old
        (f)
        (n/with-logging-context context-new
          (is (= "10" (n/context-val :foo)))
          (is (= "quux" (n/context-val :baz)) "Delta context override must not remove non-overridden attributes")
          (is (= "baz" (n/context-val :bar))))
        (with-redefs [cambium.core/stringify-val   cambium.core/encode-val
                      cambium.core/destringify-val cambium.core/decode-val]
          (n/with-logging-context nested-diff
            (is (= {"learn-to-fly" {"title" "learn to fly"
                                    "year" 1999}
                    "best-of-you"  {"title" "best of you"
                                    "year" 2005}}
                  (n/context-val :foo-fighter))
              "nested map comes out preserved as a map")
            (is (= {"title" "learn to fly"
                    "year" 1999}
                  (n/context-val [:foo-fighter :learn-to-fly]))
              "deep nested map comes out as a map")
            (n/with-logging-context {[:foo-fighter :learn-to-fly :year] 2000}
              (is (= {"title" "learn to fly"
                      "year" 2000}
                    (n/context-val [:foo-fighter :learn-to-fly])))))))
      (n/with-logging-context context-old
        (f)
        (n/with-logging-context context-new
          (is (= "10" (n/context-val :foo)))
          (is (= "quux" (n/context-val :baz)) "Delta context override must not remove non-overridden attributes")
          (is (= "baz" (n/context-val :bar)))))
      (is (nil? (n/context-val :foo)) "Attribute not set must be absent after restoration"))
    (testing "wrap-raw-mdc"
      (is (nil? (n/context-val :foo)))
      ((n/wrap-logging-context context-old f))
      ((n/wrap-logging-context context-old f) :dummy :arg)
      ((comp (partial n/wrap-logging-context context-new) (n/wrap-logging-context context-old f)))
      (is (nil? (n/context-val :foo))))))
