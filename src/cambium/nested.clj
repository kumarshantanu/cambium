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
    [cambium.mdc      :as m]
    [cambium.type     :as t])
  (:import
    [java.util ArrayList HashMap Map$Entry]))


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
  ([k]
    (nested-context-val c/current-mdc-context k))
  ([repo k]
    (let [mdc-val #(c/destringify-val (t/get-val repo (c/stringify-key %)))]
      (if (coll? k)
        (get-in (mdc-val (first k)) (map c/stringify-key (next k)))
        (mdc-val k)))))


(defn merge-nested-context!
  "Merge given 'potentially-nested' context map into the current MDC using the following constraints:
  * Entries with nil key are ignored
  * Nil values are considered as deletion-request for corresponding keys
  * Collection keys are treated as key-path (all tokens in a key path are turned into string)
  * Keys are converted to string"
  ([context]
    (merge-nested-context! c/current-mdc-context context))
  ([dest context]
    (let [^HashMap delta (HashMap. (count context))
          deleted-keys   (ArrayList.)
          remove-key     (fn [^String str-k] (.remove delta str-k) (.add deleted-keys str-k))]
      ;; build up a delta with top-level stringified keys and original vals
      (doseq [^Map$Entry entry (seq context)]
        (let [k (.getKey entry)
              v (.getValue entry)]
          (when-not (nil? k)
            (if (coll? k)
              (when (and (seq k) (every? #(not (nil? %)) k))
                (let [k-path (map c/stringify-key k)
                      k-head (first k-path)
                      k-next (next k-path)]
                  (if (and (nil? v) (not k-next))  ; consider nil values as deletion request
                    (remove-key k-head)
                    (.put delta k-head (let [value-map (or (get delta k-head)
                                                         (when-let [oldval (t/get-val dest k-head)]
                                                           (let [oldmap (c/destringify-val oldval)]
                                                             (if (map? oldmap) oldmap {}))))]
                                         (if (nil? v)  ; consider nil values as deletion request
                                           (if (next k-next)
                                             (update-in value-map (butlast k-next) dissoc (last k-next))
                                             (dissoc value-map (first k-next)))
                                           (assoc-in value-map k-next (i/stringify-nested-keys c/stringify-key v))))))))
              (if (nil? v)  ; consider nil values as deletion request
                (remove-key (c/stringify-key k))
                (.put delta (c/stringify-key k) (i/stringify-nested-keys c/stringify-key v)))))))
      ;; set the pairs from delta into the MDC
      (doseq [^Map$Entry pair (.entrySet delta)]
        (let [str-k (.getKey pair)
              v     (.getValue pair)]
          (t/put! dest str-k (c/stringify-val v))))
      ;; remove keys identified for deletion
      (doseq [^String str-k deleted-keys]
        (t/remove! dest str-k)))))
