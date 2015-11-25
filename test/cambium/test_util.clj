(ns cambium.test-util
  (:require
    [cambium.core :refer :all]))


(deflogger metrics "METRICS")


(deflogger txn-metrics "TXN-METRICS" :info :fatal)
