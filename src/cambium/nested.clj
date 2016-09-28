;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns cambium.nested
  "API for handling nested context.
  Vars 'cambium.core/stringify-val' and 'cambium.core/destringify-val' must be redefined to preserve structure.
  See: cambium.core/encode-val, cambium.core/decode-val"
  (:require
    [clojure.tools.logging      :as ctl]
    [clojure.tools.logging.impl :as ctl-impl]
    [cambium.core     :as c]
    [cambium.internal :as i]
    [cambium.mdc      :as m])
  (:import
    [java.util HashMap Map$Entry]
    [org.slf4j MDC]))


(defn nested-context-val
  "Return the value of the specified key (or keypath in nested structure) from the current context; behavior for
  non-existent keys would be implementation dependent - it may return nil or may throw exception."
  [k]
  (let [mdc-val #(c/destringify-val (MDC/get (c/stringify-key %)))]
    (if (coll? k)
      (get-in (mdc-val (first k)) (map c/stringify-key (next k)))
      (mdc-val k))))


(defn merge-nested-context!
  "Merge given 'potentially-nested' context map into the current MDC using the following constraints:
  * Entries with nil key or nil value are ignored
  * Collection keys are treated as key-path (all tokens in a key path are turned into string)
  * Keys are converted to string
  * When absent-k (second argument) is specified, context is set only if the key/path is absent"
  ([context]
    (let [^HashMap delta (HashMap. (count context))]
      ;; build up a delta with top-level stringified keys and original vals
      (doseq [pair (seq context)]
        (let [k (first pair)
              v (second pair)]
          (when-not (or (nil? k) (nil? v))
            (if (coll? k)
              (when (and (seq k) (every? #(not (nil? %)) k))
                (let [k-path (map c/stringify-key k)
                      k-head (first k-path)]
                  (.put delta k-head (-> (get delta k-head)
                                       (or (when-let [oldval (MDC/get k-head)]
                                             (let [oldmap (c/destringify-val oldval)]
                                               (if (map? oldmap) oldmap {}))))
                                       (assoc-in (next k-path) (i/stringify-nested-keys c/stringify-key v))))))
              (.put delta (c/stringify-key k) (i/stringify-nested-keys c/stringify-key v))))))
      ;; set the pairs from delta into the MDC
      (doseq [^Map$Entry pair (.entrySet delta)]
        (let [str-k (.getKey pair)
              v     (.getValue pair)]
          (MDC/put str-k (c/stringify-val v))))))
  ([context absent-k]
    (when-not (nested-context-val absent-k)
      (merge-nested-context! context))))
