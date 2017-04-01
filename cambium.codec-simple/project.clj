(defproject cambium/cambium.codec-simple "0.9.0"
  :description "Simple Cambium codec with no support for nested log attributes"
  :url "https://github.com/kumarshantanu/cambium"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.2.0"
  :pedantic? :abort
  :global-vars {*warn-on-reflection* true
                *unchecked-math* :warn-on-boxed}
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :c15 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :c16 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :c17 {:dependencies [[org.clojure/clojure "1.7.0"]]}
             :c18 {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :c19 {:dependencies [[org.clojure/clojure "1.9.0-alpha15"]]}
             :dln {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}})
