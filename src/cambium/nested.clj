;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns cambium.nested
  "Utility fns for handling nested context. Mostly useful for redefining vars in 'cambium.core' namespace:
  - cambium.core/stringify-val
  - cambium.core/destringify-val
  - cambium.core/context-val
  - cambium.core/merge-logging-context!"
  (:require
    [clojure.edn                :as edn]
    [clojure.tools.logging      :as ctl]
    [clojure.tools.logging.impl :as ctl-impl]
    [cambium.core     :as c]
    [cambium.internal :as i]
    [cambium.mdc      :as m])
  (:import
    [java.util HashMap Map$Entry]
    [org.slf4j MDC]))


;; ----- Codec (default: EDN codec) helper -----


(defn encode-val
  "Encode MDC value as string such that it retains type information. May be used to redefine cambium.core/stringify-val.
  See: decode-val"
  (^String [object-encoder v]
    (let [hint-str (fn ^String [^String hint v]
                     (let [^StringBuilder sb (StringBuilder. 15)]
                       (.append sb hint)
                       (.append sb v)
                       (.toString sb)))]
      (cond
        (string? v)  v  ; do not follow escape-safety due to performance
        (instance?
          clojure.lang.Named v) (name v)
        (integer? v) (hint-str "^long "    v)
        (float? v)   (hint-str "^double "  v)
        (instance?
          Boolean v) (hint-str "^boolean " v)
        :otherwise   (hint-str "^object "  (object-encoder v)))))
  (^String [v]
    (encode-val pr-str v)))


(defn decode-val
  "Decode MDC string value into the correct original type. May be used to redefine cambium.core/destringify-val.
  See: encode-val"
  ([object-decoder ^String s]
    (cond
      (nil? s)             s
      (= 0 (.length s))    s
      (= \^ (.charAt s 0)) (cond
                             (.startsWith s "^long ")    (try (Long/parseLong     (subs s 6)) (catch Exception e -314))
                             (.startsWith s "^double ")  (try (Double/parseDouble (subs s 8)) (catch Exception e -3.14))
                             (.startsWith s "^boolean ") (Boolean/parseBoolean    (subs s 9))
                             (.startsWith s "^object ")  (try (object-decoder (subs s 8)) (catch Exception e (str e)))
                             :otherwise                  s)
      :otherwise           s))
  ([^String s]
    (decode-val edn/read-string s)))


;; ----- Nested context navigation -----


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
