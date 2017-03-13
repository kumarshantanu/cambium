# cambium

Logs as data in Clojure. Uses [SLF4j](http://www.slf4j.org/)
[Mapped Diagnostic Context (MDC)](http://www.slf4j.org/api/org/slf4j/MDC.html) and
[clojure/tools.logging](https://github.com/clojure/tools.logging).

A log event should not be restricted to a generic text, e.g. `Registration failed`. It should also be able to
capture and emit associated log attributes (JSON example below, there could also be other formats):

```json
{"message": "Registration failed",
 "email": "foo@bar.com",
 "transaction-id": "3722940",
 "referral-code": "3FG62"}
```


## Usage

Leiningen coordinates:

| Leiningen artifact                                  | Description                                |
|-----------------------------------------------------|--------------------------------------------|
| `[cambium/cambium.core           "0.9.0-SNAPSHOT"]` | User facing core features                  |
| `[cambium/cambium.codec-simple   "0.9.0-SNAPSHOT"]` | Simple codec, not nesting-aware            |
| `[cambium/cambium.codec-cheshire "0.9.0-SNAPSHOT"]` | JSON codec using Cheshire, nesting-capable |


### What is a Cambium codec?

A Cambium codec governs how the log attributes are encoded and decoded before they are effectively sent to a log
layout. A codec constitutes the following:

| Var in `cambium.codec` ns       | Type    | Description |
|---------------------------------|---------|-------------|
| `cambium.codec/nested-nav?`     | Boolean | `true` makes Cambium treat context read/write in nesting-aware fashion |
| `cambium.codec/stringify-key`   | Fn/1    | Encodes log attribute key as string   |
| `cambium.codec/stringify-val`   | Fn/1    | Encodes log attribute value as string |
| `cambium.codec/destringify-val` | Fn/1    | Decodes log attribute from string form |

_Note: You need only one codec implementation in a project._


### Quickstart

Cambium only wraps over SLF4j. You also need a suitable SLF4j implementation, such as
[Logback](http://logback.qos.ch/),
[Log4j2](https://logging.apache.org/log4j/2.x/) or
[Log4j](http://logging.apache.org/log4j/1.2/) in your project dependencies.

Dependencies (see [logback-bundle](https://github.com/kumarshantanu/logback-bundle) for Logback artifacts):

```clojure
[cambium/cambium.core         "0.9.0-SNAPSHOT"]
[cambium/cambium.codec-simple "0.9.0-SNAPSHOT"]
[logback-bundle/json-bundle   "0.2.4"]
```

Logback configuration (file `logback.xml` in project `resources` folder):

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
          <layout class="logback_bundle.json.FlatJsonLayout">
            <jsonFormatter class="ch.qos.logback.contrib.jackson.JacksonJsonFormatter">
              <!-- prettyPrint is probably ok in dev, but usually not ideal in production: -->
              <prettyPrint>true</prettyPrint>
            </jsonFormatter>
            <!-- <context>api</context> -->
            <timestampFormat>yyyy-MM-dd'T'HH:mm:ss.SSS'Z'</timestampFormat>
            <timestampFormatTimezoneId>UTC</timestampFormatTimezoneId>
            <appendLineSeparator>true</appendLineSeparator>
          </layout>
        </encoder>
    </appender>
    <root level="debug">
      <appender-ref ref="STDOUT" />
    </root>
</configuration>
```


### Requiring the namespaces:

```clojure
(require '[cambium.core :as log])
(require '[cambium.mdc  :as mlog])
```


### Namespace based loggers

Like `clojure.tools.logging/<log-level>`, Cambium defines namespace loggers for various levels:

```clojure
(log/info "this is a log message")                                          ; simple message logging
(log/info {:latency-ms 331 :module "registration"} "App registered")        ; context and message
(log/debug {:module "order-processing"} "sequence-id verified")
(log/error {:module "user-feedback"} exception "Email notification failed") ; context, exception and message
```

Available log levels: `trace`, `debug`, `info`, `warn`, `error`, `fatal`


### Custom loggers

You can define custom loggers that you can use from any namespace as follows:

```clojure
(log/deflogger metrics "METRICS")
(log/deflogger txn-log "TXN-LOG" :info :fatal)

(metrics {:latency-ms 331 :module "registration"} "app.registration.success") ; context and message
(txn-log {:module "order-processing"} exception "Stock unavailable")          ; context, exception and message
(txn-log "Order processed")                                                   ; simple message logging
```


### Context propagation

Value based context can be propagated as follows:

```clojure
;; Propagate specified context in current thread
(log/with-logging-context {:user-id "X1234"}
  ...
  (log/info {:job-id 89} "User was assigned a new job")
  ...)

;; wrap an existing fn with specified context
(log/wrap-logging-context {:user-id "X1234"} user-assign-job)  ; creates a wrapped fn inheriting specified context
```


#### MDC propagation

Unlike value based propagation MDC propagation happens wholesale, i.e. the entire current MDC map is replaced with a
new map. Also, no conversion is applied to MDC; they are required to have string keys and values. See example below:

```clojure
;; Propagate specified context in current thread
(mlog/with-raw-mdc {"userid" "X1234"}
  ...
  (log/info {:job-id 89} "User was assigned a new job")
  ...)

;; wrap an existing fn with specified context
(mlog/wrap-raw-mdc user-assign-job)  ; creates a wrapped fn that inherits current context
(mlog/wrap-raw-mdc {"userid" "X1234"} user-assign-job)  ; creates a wrapped fn that inherits specified context
```

#### Nested context

Context values sometimes may be nested and need manipulation. Cambium requires nesting aware codec for dealing with
nested context.

Example dependencies:

```clojure
[cambium/cambium.core           "0.9.0-SNAPSHOT"]
[cambium/cambium.codec-cheshire "0.9.0-SNAPSHOT"]  ; nesting-capable codec
[logback-bundle/json-bundle     "0.2.4"]
```

One-time initialization in the project:

```clojure
(require '[cheshire.core :as cheshire])
(import '[logback_bundle.json FlatJsonLayout ValueDecoder])
(FlatJsonLayout/setGlobalDecoder
  (reify ValueDecoder
    (decode [this encoded-value] (cheshire/parse-string encoded-value))))
```

See nesting-navigation example below:

```clojure
(c/with-logging-context {:order {:client "XYZ Corp"
                                 :item-count 10}}
  ;; ..other processing..
  (c/with-logging-context {[:order :id] "F-123456"}
    ;; here the context will be {"order" {"client" "XYZ Corp" "item-count" 10 "id" "F-123456"}}
    (c/info "Order processed successfully")))
;; Logging API in the 'nested' namespace accepts nested MDC
(c/info {:order {:event-id "foo"}} "Foo happened")
```

## License

Copyright © 2015-2017 Shantanu Kumar (kumar.shantanu@gmail.com, shantanu.kumar@concur.com)

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
