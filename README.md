# cambium

Clojure wrapper for [SLF4j](http://www.slf4j.org/) with
[Mapped Diagnostic Context (MDC)](http://www.slf4j.org/api/org/slf4j/MDC.html) and
[clojure/tools.logging](https://github.com/clojure/tools.logging).

## Usage

Leiningen coordinates: `[cambium "0.1.0-SNAPSHOT"]`

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


## License

Copyright © 2015 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
