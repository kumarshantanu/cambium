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


(defn context-val
  "Return the value of the specified key (or keypath in nested structure) from the current context; behavior for
  non-existent keys would be implementation dependent - it may return nil or may throw exception."
  [k]
  (let [mdc-val #(c/destringify-val (MDC/get (c/stringify-key %)))]
    (if (coll? k)
      (get-in (mdc-val (first k)) (map c/stringify-key (next k)))
      (mdc-val k))))


(defn merge-logging-context!
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
    (when-not (context-val absent-k)
      (merge-logging-context! context))))


(defmacro with-logging-context
  "Given 'potentially-nested' context map data, merge it into the current MDC and evaluate the body of code in that
  context. Restore original context in the end.
  See: cambium.nested/merge-logging-context!, cambium.mdc/with-raw-mdc
       http://logback.qos.ch/manual/mdc.html"
  [context & body]
  `(m/preserving-mdc
     (merge-logging-context! ~context)
     ~@body))


(defn wrap-logging-context
  "Wrap function f with the specified 'potentially nested' context map.
  See: cambium.mdc/wrap-raw-mdc
       http://logback.qos.ch/manual/mdc.html"
  [context f]
  (fn
    ([]
      (with-logging-context context
        (f)))
    ([x]
      (with-logging-context context
        (f x)))
    ([x y]
      (with-logging-context context
        (f x y)))
    ([x y & args]
      (with-logging-context context
        (apply f x y args)))))


;; ----- logging calls -----


(defmacro log
  "Log an event or message under specified logger and log-level."
  ([level msg]
    `(log (ctl-impl/get-logger ctl/*logger-factory* ~*ns*) ~level ~msg))
  ([level mdc throwable msg]
    `(log (ctl-impl/get-logger ctl/*logger-factory* ~*ns*) ~level ~mdc ~throwable ~msg))
  ([logger level msg]
    `(when (ctl-impl/enabled? ~logger ~level)
       (ctl-impl/write! ~logger ~level nil ~msg)))
  ([logger level mdc throwable msg]
    `(when (ctl-impl/enabled? ~logger ~level)
       (with-logging-context ~mdc
         (ctl-impl/write! ~logger ~level ~throwable ~msg)))))


(defmacro ^:private deflevel
  "This macro is used internally to only define normal namespace-based level loggers."
  [level-sym]
  (when-not (symbol? level-sym)
    (throw (IllegalArgumentException. (str "Expected a symbol for level name, found " (pr-str level-sym)))))
  (let [level-key (keyword level-sym)
        level-doc (str "Similar to clojure.tools.logging/" level-sym ".")
        arglists  ''([msg] [mdc msg] [mdc throwable msg])]
    `(defmacro ~level-sym
       ~level-doc
       {:arglists ~arglists}
       ([msg#]                 `(log ~~level-key ~msg#))
       ([mdc# msg#]            `(log ~~level-key ~mdc# nil ~msg#))
       ([mdc# throwable# msg#] `(log ~~level-key ~mdc# ~throwable# ~msg#)))))


(deflevel trace)
(deflevel debug)
(deflevel info)
(deflevel warn)
(deflevel error)
(deflevel fatal)
