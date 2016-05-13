# cambium

Clojure wrapper for [SLF4j](http://www.slf4j.org/) with
[Mapped Diagnostic Context (MDC)](http://www.slf4j.org/api/org/slf4j/MDC.html) and
[clojure/tools.logging](https://github.com/clojure/tools.logging).

## Usage

Leiningen coordinates: `[cambium "0.3.3"]`

_Note: Cambium only wraps over SLF4j. You also need a suitable SLF4j implementation, such as
[logback-bundle](https://github.com/kumarshantanu/logback-bundle) as your project dependency._

Require the namespace:

```clojure
(require '[cambium.core :as c])
```


### Namespace based loggers

Like `clojure.tools.logging/<log-level>`, Cambium defines namespace loggers for various levels:

```clojure
(c/info "this is a log message")                                          ; simple message logging
(c/info {:latency-ms 331 :module "registration"} "App registered")        ; context and message
(c/debug {:module "order-processing"} "sequence-id verified")
(c/error {:module "user-feedback"} exception "Email notification failed") ; context, exception and message
```

Available log levels: `trace`, `debug`, `info`, `warn`, `error`, `fatal`


### Custom loggers

You can define custom loggers that you can use from any namespace as follows:

```clojure
(c/deflogger metrics "METRICS")
(c/deflogger txn-log "TXN-LOG" :info :fatal)

(metrics {:latency-ms 331 :module "registration"} "app.registration.success") ; context and message
(txn-log {:module "order-processing"} exception "Stock unavailable")          ; context, exception and message
(txn-log "Order processed")                                                   ; simple message logging
```


## API Documentation

http://kumarshantanu.github.io/projects/cambium/


## License

Copyright © 2015-2016 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
