;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns cambium.codec-simple-test
  (:require
    [clojure.test :refer :all]
    [cambium.codec :as codec]))


(deftest test-nesting-support
  (is (false? codec/nested-nav?)))


(deftest test-stringify-key
  (testing "string keys"
    (is (= "foo"     (codec/stringify-key "foo"))))
  (testing "keyword keys"
    (is (= "foo"     (codec/stringify-key :foo)))
    (is (= "foo/bar" (codec/stringify-key :foo/bar)))))


(deftest test-stringify-val
  (testing "string vals"
    (is (= "foo"     (codec/stringify-val "foo"))))
  (testing "keyword vals"
    (is (= "foo"     (codec/stringify-val :foo)))
    (is (= "foo/bar" (codec/stringify-val :foo/bar))))
  (testing "numeric vals"
    (is (= "100"     (codec/stringify-val 100)))
    (is (= "1.2"     (codec/stringify-val 1.2))))
  (testing "random objects"
    (is (= "foo[a-zA-Z]+bar" (codec/stringify-val #"foo[a-zA-Z]+bar")))
    (is (string?             (codec/stringify-val (Object.))))))


(deftest test-destringify-val
  (is (= "foo" (codec/destringify-val "foo")))
  (is (= "100" (codec/destringify-val "100")))
  (is (= "1.2" (codec/destringify-val "1.2"))))
