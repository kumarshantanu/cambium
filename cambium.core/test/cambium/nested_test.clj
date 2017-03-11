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
    [cambium.codec  :as codec]
    [cambium.core   :as c]
    [cambium.nested :as n]
    [cambium.test-util :as tu]))


(deftest type-safe-encoding
  (testing "type-safe encoding"
    (let [sk codec/stringify-key]
      (with-redefs [codec/stringify-key (fn ^String [x] (.replace ^String (sk x) \- \_))
                    codec/stringify-val n/encode-val
                    codec/destringify-val n/decode-val]
        (c/info "hello")
        (c/info {:foo-k "bar" :baz 10 :qux true} "hello with context")
        (c/with-logging-context {:extra-k "context" "some-data" [1 2 :three 'four]}
          (is (= (c/get-context) {"extra_k" "context" "some_data" [1 2 :three 'four]}))
          (is (= (c/context-val :extra-k) "context"))
          (is (nil? (c/context-val "foo")))
          (c/info {:foo "bar"} "hello with wrapped context"))
        (c/error {} (ex-info "some error" {:data :foo}) "internal error")))))


(deftest test-codec
  (let [payload (n/encode-val :foo)]  (is (= "foo" payload))           (is (= "foo" (n/decode-val payload))))
  (let [payload (n/encode-val 'foo)]  (is (= "foo" payload))           (is (= "foo" (n/decode-val payload))))
  (let [payload (n/encode-val "foo")] (is (= "foo" payload))           (is (= "foo" (n/decode-val payload))))
  (let [payload (n/encode-val 10)]    (is (= "^long 10" payload))      (is (= 10    (n/decode-val payload))))
  (let [payload (n/encode-val 1.2)]   (is (= "^double 1.2" payload))   (is (= 1.2   (n/decode-val payload))))
  (let [payload (n/encode-val true)]  (is (= "^boolean true" payload)) (is (= true  (n/decode-val payload))))
  (let [payload (n/encode-val nil)]   (is (= "^object nil" payload))   (is (= nil   (n/decode-val payload))))
  (let [payload (n/encode-val
                  [1 :two 'four])]    (is (= "^object [1 :two four]"
                                            payload))                  (is (= [1 :two 'four] (n/decode-val payload)))))


(deftest log-test
  (with-redefs [c/context-val n/nested-context-val
                c/merge-logging-context! n/merge-nested-context!]
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
      (tu/metrics {[:app :module] "registration"} (ex-info "some error" {:data :foo}) "internal error")
      (tu/txn-metrics {:module "order-fetch"} "Fetched order #4568"))
    (testing "type-safe encoding"
      (let [sk codec/stringify-key]
        (with-redefs [codec/stringify-key (fn ^String [x] (.replace ^String (sk x) \- \_))
                      codec/stringify-val n/encode-val
                      codec/destringify-val n/decode-val]
          (c/info "hello")
          (c/info {:foo-k "bar" :baz 10 :qux true} "hello with context")
          (c/with-logging-context {:extra-k "context" "some-data" [1 2 :three 'four]}
            (is (= (c/get-context) {"extra_k" "context" "some_data" [1 2 :three 'four]}))
            (is (= (c/context-val :extra-k) "context"))
            (is (nil? (c/context-val "foo")))
            (c/info {:foo "bar"} "hello with wrapped context"))
          (c/error {} (ex-info "some error" {:data :foo}) "internal error"))))))


(deftest test-context-propagation
  (with-redefs [codec/stringify-val n/encode-val
                codec/destringify-val n/decode-val
                c/context-val n/nested-context-val
                c/merge-logging-context! n/merge-nested-context!]
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
               (is (= "bar"  (c/context-val :foo)))
               (is (= "quux" (c/context-val :baz)))
               (is (nil? (c/context-val :bar))))
             ([dummy arg]))]
     (testing "with-raw-mdc"
       (is (nil? (c/context-val :foo)) "Attribute not set must be absent before override")
       (c/with-logging-context context-old
         (f)
         (c/with-logging-context context-new
           (is (= 10 (c/context-val :foo)))
           (is (= "quux" (c/context-val :baz)) "Delta context override must not remove non-overridden attributes")
           (is (= "baz" (c/context-val :bar))))
         (with-redefs [codec/stringify-val   n/encode-val
                       codec/destringify-val n/decode-val]
           (c/with-logging-context nested-diff
             (is (= {"learn-to-fly" {"title" "learn to fly"
                                     "year" 1999}
                     "best-of-you"  {"title" "best of you"
                                     "year" 2005}}
                   (c/context-val :foo-fighter))
               "nested map comes out preserved as a map")
             (is (= {"title" "learn to fly"
                     "year" 1999}
                   (c/context-val [:foo-fighter :learn-to-fly]))
               "deep nested map comes out as a map")
             (c/with-logging-context {[:foo-fighter :learn-to-fly :year] 2000}
               (is (= {"title" "learn to fly"
                       "year" 2000}
                     (c/context-val [:foo-fighter :learn-to-fly])))))))
       (c/with-logging-context context-old
         (f)
         (c/with-logging-context context-new
           (is (= 10 (c/context-val :foo)))
           (is (= "quux" (c/context-val :baz)) "Delta context override must not remove non-overridden attributes")
           (is (= "baz" (c/context-val :bar)))))
       (is (nil? (c/context-val :foo)) "Attribute not set must be absent after restoration"))
     (testing "deletion via nil values"
       (c/with-logging-context context-old
         (c/with-logging-context {:foo nil}
           (is (not (contains? (c/get-context) (codec/stringify-key :foo))))))
       (c/with-logging-context {:foo {:bar {:baz 10}}}
         (c/with-logging-context {[:foo :bar :baz] nil}
           (is (= {(codec/stringify-key :bar) {}} (c/context-val :foo))))
         (c/with-logging-context {[:foo :bar] nil}
           (is (= {} (c/context-val :foo))))
         (c/with-logging-context {[:foo] nil}
           (is (not (contains? (c/get-context) (codec/stringify-key :foo)))))))
     (testing "wrap-raw-mdc"
       (is (nil? (c/context-val :foo)))
       ((c/wrap-logging-context context-old f))
       ((c/wrap-logging-context context-old f) :dummy :arg)
       ((comp (partial c/wrap-logging-context context-new) (c/wrap-logging-context context-old f)))
       (is (nil? (c/context-val :foo)))))))