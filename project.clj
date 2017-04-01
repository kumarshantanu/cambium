(defproject cambium/cambium-parent "not-released"
  :description "Logs as data with SLF4j MDC and clojure/tools.logging"
  :url "https://github.com/kumarshantanu/cambium"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :min-lein-version "2.2.0"
  :pedantic? :abort
  :plugins [[lein-sub "0.3.0"]]
  :sub ["cambium.codec-simple" "cambium.codec-cheshire" "cambium.core"])
