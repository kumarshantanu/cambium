(defproject cambium/cambium.core "0.9.0"
  :description "Clojure wrapper for SLF4j with MDC and clojure/tools.logging"
  :url "https://github.com/kumarshantanu/cambium"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.2.0"
  :pedantic? :abort
  :dependencies [[org.slf4j/slf4j-api       "1.7.25"]
                 [org.clojure/tools.logging "0.3.1" :exclusions [org.clojure/clojure]]]
  :global-vars {*warn-on-reflection* true}
  :profiles {:provided {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :codec-simple {:dependencies [[cambium/cambium.codec-simple "0.9.0"]]}
             :nested-test  {:test-paths ["nested-test"]}
             :dev {:dependencies [[org.clojure/tools.nrepl "0.2.10"]]}
             :logback {:dependencies [[ch.qos.logback/logback-classic "1.1.7"]
                                      [ch.qos.logback/logback-core    "1.1.7"]]}
             :log4j12 {:dependencies [[org.slf4j/slf4j-log4j12 "1.7.21"]
                                      [log4j/log4j "1.2.17"]]}
             :log4j2  {:dependencies [[org.apache.logging.log4j/log4j-api  "2.6.2"]
                                      [org.apache.logging.log4j/log4j-core "2.6.2"]
                                      [org.apache.logging.log4j/log4j-slf4j-impl "2.6.2"]]}
             :c15 {:dependencies [[org.clojure/clojure "1.5.1"]]}
             :c16 {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :c17 {:dependencies [[org.clojure/clojure "1.7.0"]]
                   :global-vars  {*unchecked-math* :warn-on-boxed}}
             :c18 {:dependencies [[org.clojure/clojure "1.8.0"]]
                   :global-vars  {*unchecked-math* :warn-on-boxed}}
             :c19 {:dependencies [[org.clojure/clojure "1.9.0-alpha15"]]
                   :global-vars  {*unchecked-math* :warn-on-boxed}}
             :dln {:jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}
  :plugins [[lein-codox "0.10.0"]]
  :codox {:namespaces [cambium.core cambium.mdc]
          :source-uri "http://github.com/kumarshantanu/cambium/blob/v{version}/{filepath}#L{line}"})
