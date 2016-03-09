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
    [cambium.internal           :as i])
  (:import
    [org.slf4j MDC]))


(defn get-context
  "Return a copy of the current context containing string keys and values."
  ^java.util.Map []
  (MDC/getCopyOfContextMap))


(defn context-val
  "Return the value of the specified key from the current context; behavior for non-existent keys would be
  implemnentation dependent - it may return nil or may throw exception."
  ^java.lang.String [k]
  (MDC/get (i/as-str k)))


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
        (string? v)  v
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


(def stringify-val
  "Arity-1 fn to convert MDC value into a string. By default this carries out a plain string conversion.
  See: encode-val"
  i/as-str)


(defn set-logging-context!
  "Set the logging context using specified map data, unless the specified identifier key already exists.
  Nil keys and values are ignored."
  ([context]
    (i/do-pairs context k v
      (when-not (or (nil? k) (nil? v))
        (MDC/put (i/as-str k) (stringify-val v)))))
  ([context k]
    (when-not (MDC/get (i/as-str k))
      (set-logging-context! context))))


(defmacro with-logging-context
  "Given map data (as context) and a body of code, set map data as Mapped Diagnostic Context (MDC) and execute body of
  code in that context. Nil keys and values are ignored.
  See also: http://logback.qos.ch/manual/mdc.html"
  [context & body]
  `(let [context# ~context
         old-ctx# (MDC/getCopyOfContextMap)]
     (try
       (set-logging-context! context#)
       ~@body
       (finally
         (if old-ctx#
           (MDC/setContextMap old-ctx#)
           (MDC/clear))))))


(defmacro log
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
