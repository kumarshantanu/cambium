(ns cambium.core-test
  (:require [clojure.test :refer :all]
            [cambium.core :as c]
            [cambium.test-util :as tu]))


(deftest log-test
  (testing "Normal scenarios"
    (c/info "hello")
    (c/info {:foo "bar"} "hello with context")
    (c/with-logging-context {:extra "context"}
      (c/info {:foo "bar"} "hello with wrapped context"))
    (c/error {} (ex-info "some error" {:data :foo}) "internal error"))
  (testing "custom loggers"
    (tu/metrics {:latency-ns 430 :module "registration"} "op.latency")
    (tu/metrics {:module "registration"} (ex-info "some error" {:data :foo}) "internal error")
    (tu/txn-metrics {:module "order-fetch"} "Fetched order #4568")))
