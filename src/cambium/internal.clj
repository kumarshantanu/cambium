;   Copyright (c) Shantanu Kumar. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file LICENSE at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


(ns cambium.internal
  (:require
    [clojure.tools.logging      :as ctl]
    [clojure.tools.logging.impl :as ctl-impl]))


(defmacro strcat
  "Stripped down impl of Stringer/strcat: https://github.com/kumarshantanu/stringer"
  ([]
    "")
  ([token]
    (if (or (string? token)
          (keyword? token)
          (number? token))
      (str token)
      `(let [x# ~token]
         (if (nil? x#)
           ""
           (String/valueOf x#))))))


(defn as-str
  "Turn anything into string"
  ^String [x]
  (cond
    (instance? clojure.lang.Named x) (name x)
    (string? x)                      x
    :otherwise                       (strcat x)))


(defmacro do-pairs
  "Given a map m, loop over its pairs binding k-sym and v-sym to respective key and value, executing body of code for
  each pair."
  [m k-sym v-sym & body]
  `(loop [coll# (seq ~m)]
     (when coll#
       (let [[~k-sym ~v-sym] (first coll#)]
         ~@body)
       (recur (next coll#)))))
