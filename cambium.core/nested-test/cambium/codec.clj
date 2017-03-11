;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns cambium.codec
  "Nested Cambium EDN-based codec for testing purposes."
  (:require
    [cambium.codec.util :as u]))


;; ----- fns below are part of the contract -----


(def ^:const nested-nav?
  "Boolean value - whether this codec supports nested (navigation of) log attributes."
  true)


(defn stringify-key
  "Arity-1 fn to convert MDC key into a string."
  ^String [x]
  (u/as-str x))


(defn stringify-val
  "Arity-1 fn to convert MDC value into a string."
  ^String [x]
  (u/encode-val x))


(defn destringify-val
  "Arity-1 fn to convert MDC string back to original value."
  [^String x]
  (u/decode-val x))
