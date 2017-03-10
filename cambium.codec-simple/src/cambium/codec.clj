(ns cambium.codec
  "Simple Cambium codec implementation with no support for nested log attributes.")


(defn- as-str
  "Turn given argument into string."
  ^String [^Object x]
  (cond
    (instance?
      clojure.lang.Named x) (if-let [^String the-ns (namespace x)]
                              (let [^StringBuilder sb (StringBuilder. the-ns)]
                                (.append sb \/)
                                (.append sb (name x))
                                (.toString sb))
                              (name x))
    (instance? String x)    x
    (nil? x)                ""
    :otherwise              (.toString x)))


;; ----- fns below are part of the contract -----


(def ^:const nested-nav?
  "Boolean value - whether this codec supports nested (navigation of) log attributes. This codec sets it to false."
  false)


(defn stringify-key
  "Arity-1 fn to convert MDC key into a string. This codec carries out a plain string conversion."
  ^String [x]
  (as-str x))


(defn stringify-val
  "Arity-1 fn to convert MDC value into a string. This codec carries out a plain string conversion."
  ^String [x]
  (as-str x))


(defn destringify-val
  "Arity-1 fn to convert MDC string back to original value. This codec simply returns the supplied value."
  [^String x]
  x)
