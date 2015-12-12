;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns cambium.core-test
  (:require [clojure.test :refer :all]
            [cambium.core :as c]
            [cambium.test-util :as tu]))


(deftest log-test
  (testing "Normal scenarios"
    (c/info "hello")
    (c/info {:foo "bar"} "hello with context")
    (c/with-logging-context {:extra "context"}
      (is (= (c/get-context) {"extra" "context"}))
      (is (= (c/context-val :extra) "context"))
      (is (nil? (c/context-val "foo")))
      (c/info {:foo "bar"} "hello with wrapped context"))
    (c/error {} (ex-info "some error" {:data :foo}) "internal error"))
  (testing "custom loggers"
    (tu/metrics {:latency-ns 430 :module "registration"} "op.latency")
    (tu/metrics {:module "registration"} (ex-info "some error" {:data :foo}) "internal error")
    (tu/txn-metrics {:module "order-fetch"} "Fetched order #4568")))
