;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns cambium.core-test
  (:require
    [clojure.test :refer :all]
    [cambium.core :as c]
    [cambium.test-util :as tu]))


(deftest log-test
  (testing "Normal scenarios"
    (c/info "hello")
    (c/info {:foo "bar" :baz 10 :qux true} "hello with context")
    (c/with-logging-context {:extra "context" "data" [1 2 :three 'four]}
      (is (= (c/get-context) {"extra" "context" "data" "[1 2 :three four]"}))
      (is (= (c/context-val :extra) "context"))
      (is (nil? (c/context-val "foo")))
      (c/info {:foo "bar"} "hello with wrapped context"))
    (c/error {} (ex-info "some error" {:data :foo}) "internal error"))
  (testing "custom loggers"
    (tu/metrics {:latency-ns 430 :module "registration"} "op.latency")
    (tu/metrics {:module "registration"} (ex-info "some error" {:data :foo}) "internal error")
    (tu/txn-metrics {:module "order-fetch"} "Fetched order #4568"))
  (testing "type-safe encoding"
    (alter-var-root #'c/stringify-key (fn [f]
                                        (fn ^String [x] (.replace ^String (f x) \- \_))))
    (alter-var-root #'c/stringify-val (constantly c/encode-val))
    (alter-var-root #'c/destringify-val (constantly c/decode-val))
    (c/info "hello")
    (c/info {:foo-k "bar" :baz 10 :qux true} "hello with context")
    (c/with-logging-context {:extra-k "context" "some-data" [1 2 :three 'four]}
      (is (= (c/get-context) {"extra_k" "context" "some_data" [1 2 :three 'four]}))
      (is (= (c/context-val :extra-k) "context"))
      (is (nil? (c/context-val "foo")))
      (c/info {:foo "bar"} "hello with wrapped context"))
    (c/error {} (ex-info "some error" {:data :foo}) "internal error")))


(deftest test-codec
  (let [payload (c/encode-val :foo)]  (is (= "foo" payload))           (is (= "foo" (c/decode-val payload))))
  (let [payload (c/encode-val 'foo)]  (is (= "foo" payload))           (is (= "foo" (c/decode-val payload))))
  (let [payload (c/encode-val "foo")] (is (= "foo" payload))           (is (= "foo" (c/decode-val payload))))
  (let [payload (c/encode-val 10)]    (is (= "^long 10" payload))      (is (= 10    (c/decode-val payload))))
  (let [payload (c/encode-val 1.2)]   (is (= "^double 1.2" payload))   (is (= 1.2   (c/decode-val payload))))
  (let [payload (c/encode-val true)]  (is (= "^boolean true" payload)) (is (= true  (c/decode-val payload))))
  (let [payload (c/encode-val nil)]   (is (= "^object nil" payload))   (is (= nil   (c/decode-val payload))))
  (let [payload (c/encode-val
                  [1 :two 'four])]    (is (= "^object [1 :two four]"
                                            payload))                  (is (= [1 :two 'four] (c/decode-val payload)))))
