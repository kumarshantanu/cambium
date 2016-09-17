# cambium

Clojure wrapper for [SLF4j](http://www.slf4j.org/) with
[Mapped Diagnostic Context (MDC)](http://www.slf4j.org/api/org/slf4j/MDC.html) and
[clojure/tools.logging](https://github.com/clojure/tools.logging).

## Usage

Leiningen coordinates: `[cambium "0.6.0"]`

_Note: Cambium only wraps over SLF4j. You also need a suitable SLF4j implementation, such as
[logback-bundle](https://github.com/kumarshantanu/logback-bundle) as your project dependency._

Require the namespace:

```clojure
(require '[cambium.core :as c])
(require '[cambium.mdc  :as m])
(require '[cambium.nested :as n])
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


### Context propagation

Value based context can be propagated as follows:

```clojure
;; Propagate specified context in current thread
(c/with-logging-context {:user-id "X1234"}
  ...
  (c/info {:job-id 89} "User was assigned a new job")
  ...)

;; wrap an existing fn with specified context
(c/wrap-logging-context {:user-id "X1234"} user-assign-job)  ; creates a wrapped fn that inherits specified context
```

#### MDC propagation

Unlike value based propagation MDC propagation happens wholesale, i.e. the entire current MDC map is replaced with a
new map. Also, no conversion is applied to MDC; they are required to have string keys and values. See example below:

```clojure
;; Propagate specified context in current thread
(m/with-raw-mdc {"userid" "X1234"}
  ...
  (c/info {:job-id 89} "User was assigned a new job")
  ...)

;; wrap an existing fn with specified context
(m/wrap-raw-mdc user-assign-job)  ; creates a wrapped fn that inherits current context
(m/wrap-raw-mdc {"userid" "X1234"} user-assign-job)  ; creates a wrapped fn that inherits specified context
```

#### Nested context

_Note: This requires special logging layout implementation that can decode the encoded context._

Context values sometimes may be nested and need manipulation. Cambium requires format-preserving codec to be configured
ahead of using nested context:

```clojure
(alter-var-root #'cambium.core/stringify-val   (constantly cambium.core/encode-val)
(alter-var-root #'cambium.core/destringify-val (constantly cambium.core/decode-val)
```

Also, for first-class handling of nested context, Cambium converts all tokens in a key path as string tokens. See
nesting example below:

```clojure
(n/with-logging-context {:order {:client "XYZ Corp"
                                 :item-count 10}}
  ;; ..other processing..
  (n/with-logging-context {[:order :id] "F-123456"}
    ;; here the context will be {"order" {"client" "XYZ Corp" "item-count" 10 "id" "F-123456"}}
    (c/info "Order processed successfully")))
;; Logging API in the 'nested' namespace accepts nested MDC
(n/info {:order {:event-id "foo"}} "Foo happened")
```


## License

Copyright © 2015-2016 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
