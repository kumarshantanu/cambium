(defproject cambium "0.1.0-SNAPSHOT"
  :description "Clojure wrapper for SLF4j with MDC and clojure/tools.logging"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.slf4j/slf4j-api       "1.7.12"]
                 [org.clojure/tools.logging "0.3.1" :exclusions [org.clojure/clojure]]]
  :global-vars {*warn-on-reflection* true}
  :profiles {:dev {:dependencies [[org.clojure/tools.nrepl "0.2.10"]
                                  [ch.qos.logback/logback-classic "1.1.3"]
                                  [ch.qos.logback/logback-core    "1.1.3"]]}
             :c15 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :c16 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :c17 {:dependencies [[org.clojure/clojure "1.7.0"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}
             :c18 {:dependencies [[org.clojure/clojure "1.8.0-RC2"]]
                   :global-vars {*unchecked-math* :warn-on-boxed}}})
