;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns cambium.core
  (:require
    [clojure.tools.logging      :as ctl]
    [clojure.tools.logging.impl :as ctl-impl]
    [cambium.internal           :as i]
    [cambium.mdc                :as m]
    [cambium.type               :as t])
  (:import
    [java.util HashMap Map$Entry]
    [org.slf4j MDC]))


;; ----- global var hooks (with simple defaults) for MDC codec -----


(def ^:redef stringify-key
  "Arity-1 fn to convert MDC key into a string. By default this carries out a plain string conversion."
  i/as-str)


(def ^:redef stringify-val
  "Arity-1 fn to convert MDC value into a string. By default this carries out a plain string conversion."
  i/as-str)


(def ^:redef destringify-val
  "Arity-1 fn to convert MDC string back to original value. By default this simply returns the stored String value."
  identity)


;; ----- MDC read/write -----


(extend-protocol t/IMutableContext
  HashMap
  (get-val [this k]   (.get this (stringify-key k)))
  (put!    [this k v] (.put this (stringify-key k) (stringify-val v)))
  (remove! [this k]   (.remove this (stringify-key k))))


(def current-mdc-context
  (reify t/IMutableContext
    (get-val [_ k]   (MDC/get (stringify-key k)))
    (put!    [_ k v] (MDC/put (stringify-key k) (stringify-val v)))
    (remove! [_ k]   (MDC/remove (stringify-key k)))))


;; ----- MDC handling -----


(defn get-context
  "Return a copy of the current context containing string keys and original values."
  ^java.util.Map []
  (let [cm (MDC/getCopyOfContextMap)
        ks (keys cm)]
    (zipmap ks (map #(destringify-val (get cm %)) ks))))


(defn ^:redef context-val
  "Return the value of the specified key from the current context; behavior for non-existent keys would be
  implementation dependent - it may return nil or may throw exception."
  ([k]
    (context-val current-mdc-context k))
  ([repo k]
    (destringify-val (t/get-val repo (stringify-key k)))))


(defn ^:redef merge-logging-context!
  "Merge given context map into the current MDC using the following constraints:
  * Nil keys are ignored
  * Nil values are considered as deletion-request for corresponding keys
  * Keys are converted to string
  * Keys in the current context continue to have old values unless they are overridden by the specified context map
  * Keys in the context map may not be nested (for nesting support consider 'cambium.nested/merge-nested-context!')"
  ([context]
    (merge-logging-context! current-mdc-context context))
  ([dest context]
    (doseq [^Map$Entry entry (seq context)]
      (let [k (.getKey entry)
            v (.getValue entry)]
        (when-not (nil? k)
          (if (nil? v)  ; consider nil values as deletion request
            (t/remove! dest (stringify-key k))
            (t/put! dest (stringify-key k) (stringify-val v))))))))


(defmacro with-logging-context
  "Given context map data, merge it into the current MDC and evaluate the body of code in that context. Restore
  original context in the end.
  See: cambium.core/merge-logging-context!, cambium.mdc/with-raw-mdc
       http://logback.qos.ch/manual/mdc.html"
  [context & body]
  `(m/preserving-mdc
     (merge-logging-context! ~context)
     ~@body))


(defn wrap-logging-context
  "Wrap function f with the specified logging context.
  See: cambium.core/with-logging-context, cambium.mdc/wrap-raw-mdc
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
    (i/expected i/mdc-literal? "context (MDC) map" mdc)
    (i/expected i/throwable-literal? "exception object" throwable)
    `(when (ctl-impl/enabled? ~logger ~level)
       (with-logging-context ~mdc
         (ctl-impl/write! ~logger ~level ~throwable ~msg)))))


(def level-keys
  #{:trace :debug :info :warn :error :fatal})


(def level-syms
  "Symbols levels"
  '#{trace debug info warn error fatal})


(defmacro ^:private deflevel
  "This macro is used internally to only define normal namespace-based level loggers."
  [level-sym]
  (i/expected level-syms (str "a symbol for level name " level-syms) level-sym)
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
    (i/expected symbol? "a symbol for logger var name" logger-sym)
    (i/expected string? "a string logger name" logger-name)
    (i/expected level-keys (str "log-level keyword " level-keys) log-level)
    (i/expected level-keys (str "error-log-level keyword " level-keys) error-log-level)
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
