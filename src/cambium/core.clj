;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns cambium.core
  (:require
    [clojure.edn                :as edn]
    [clojure.tools.logging      :as ctl]
    [clojure.tools.logging.impl :as ctl-impl]
    [cambium.internal           :as i]
    [cambium.mdc                :as m])
  (:import
    [java.util HashMap Map$Entry]
    [org.slf4j MDC]))


;; ----- global var hooks (with simple defaults) for MDC codec -----


(def ^:redef stringify-key
  "Arity-1 fn to convert MDC key into a string. By default this carries out a plain string conversion."
  i/as-str)


(def ^:redef stringify-val
  "Arity-1 fn to convert MDC value into a string. By default this carries out a plain string conversion.
  See: encode-val"
  i/as-str)


(def ^:redef destringify-val
  "Arity-1 fn to convert MDC string back to original value. By default this simply returns the stored String value.
  See: decode-val"
  identity)


;; ----- EDN based codec helper -----


(defn encode-val
  "Encode MDC value as string such that it retains type information.
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
  "Decode MDC string value into the correct original type.
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


;; ----- MDC handling -----


(defn get-context
  "Return a copy of the current context containing string keys and original values."
  ^java.util.Map []
  (let [cm (MDC/getCopyOfContextMap)
        ks (keys cm)]
    (zipmap ks (map #(destringify-val (get cm %)) ks))))


(defn context-val
  "Return the value of the specified key from the current context; behavior for non-existent keys would be
  implementation dependent - it may return nil or may throw exception.
  See: cambium.core/encode-val, cambium.core/decode-val"
  [k]
  (destringify-val (MDC/get (stringify-key k))))


(defn merge-logging-context!
  "Merge given context map into the current MDC using the following constraints:
  * Nil keys and values are ignored
  * Keys are converted to string
  * When absent-k (second argument) is specified, context is set only if the key is absent
  * Keys in the current context continue to have old values unless they are overridden by the specified context map
  * Keys in the context map may not be nested (for nesting support consider 'cambium.core/merge-nested-context!')"
  ([context]
    (doseq [pair (seq context)]
      (let [k (first pair)
            v (second pair)]
        (when-not (or (nil? k) (nil? v))
          (MDC/put (stringify-key k) (stringify-val v))))))
  ([context absent-k]
    (when-not (MDC/get (stringify-key absent-k))
      (merge-logging-context! context))))


(defmacro with-logging-context
  "Given context map data, merge it into the current MDC and evaluate the body of code in that context. Restore
  original context in the end. Following constraints are applied:
  * Nil keys and values are ignored
  * Keys may not be nested (for nesting support consider 'cambium.core/with-nested-context')
  * Values are not inspected for nested structures
  See: cambium.core/merge-logging-context!, cambium.core/with-nested-context, cambium.mdc/with-raw-mdc
       http://logback.qos.ch/manual/mdc.html"
  [context & body]
  `(m/preserving-mdc
     (merge-logging-context! ~context)
     ~@body))


(defn wrap-logging-context
  "Wrap function f with the specified logging context. For nested context consider 'cambium.core/wrap-nested-context'.
  See: cambium.core/with-logging-context, cambium.core/wrap-nested-context, cambium.mdc/wrap-raw-mdc
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


;; ----- making custom logger -----


(defmacro deflogger
  "Define a custom logger with spcified logger name. You may optionally specify normal (:info by default) and error
  (:error by default) log levels."
  ([logger-sym logger-name]
    `(deflogger ~logger-sym ~logger-name :info :error))
  ([logger-sym logger-name log-level error-log-level]
    (when-not (symbol? logger-sym)
      (throw (IllegalArgumentException. (str "Expected a symbol for logger var name, found " (pr-str logger-sym)))))
    (when-not (string? logger-name)
      (throw (IllegalArgumentException. (str "Expected a string logger name, found " (pr-str logger-name)))))
    (when-not (#{:trace :debug :info :warn :error :fatal} log-level)
      (throw (IllegalArgumentException.
               (str "Expected log-level :trace, :debug, :info, :warn, :error or :fatal, found " (pr-str log-level)))))
    (when-not (#{:trace :debug :info :warn :error :fatal} error-log-level)
      (throw
        (IllegalArgumentException.
          (str "Expected error-log-level :trace, :debug, :info, :warn, :error or :fatal, found " (pr-str log-level)))))
    (let [docstring (str logger-name " logger.")
          arglists  ''([msg] [mdc msg] [mdc throwable msg])]
      `(defmacro ~logger-sym
         ~docstring
         {:arglists ~arglists}
         ([msg#]                 `(log (ctl-impl/get-logger ctl/*logger-factory* ~~logger-name)
                                    ~~log-level ~msg#))
         ([mdc# msg#]            `(log (ctl-impl/get-logger ctl/*logger-factory* ~~logger-name)
                                    ~~log-level ~mdc# nil ~msg#))
         ([mdc# throwable# msg#] `(log (ctl-impl/get-logger ctl/*logger-factory* ~~logger-name)
                                    ~~error-log-level ~mdc# ~throwable# ~msg#))))))
