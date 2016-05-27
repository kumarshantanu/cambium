# Changes and TODO


## 0.4.0 / 2016-May-27

* Support for propagating raw and value based MDC across contexts


## 0.3.4 / 2016-May-14

* Bump SLF4j-API dependency version to `1.7.21`
* Fix `cambium.core/get-context` to deserialize MDC values before returning


## 0.3.3 / 2016-May-13

* MDC string values deserialized back to original using `cambium.core/destringify-val` (can be redefined) 
* Use function `cambium.core/destringify-val` when obtaining context values


## 0.3.2 / 2016-March-10

* Use function `cambium.core/stringify-key` to convert MDC key to string (can be redefined)


## 0.3.1 / 2016-March-10

* Allow redefinition of `cambium.core/stringify-val` even when Clojure 1.8+ direct-linking is enabled


## 0.3.0 / 2016-March-09

* Bump SLF4j-API dependency version to `1.7.18`
* String conversion of MDC values is now via `cambium.core/stringify-val` (can be updated to suit needs)
* Helper functions `cambium.core/encode-val` and `cambium.core/decode-val` to handle type-safe encoding


## 0.2.0 / 2015-December-12

* Bump SLF4j-API dependency version to `1.7.13`
* Support for reading context values


## 0.1.0 / 2015-November-26

* Logging operations that piggyback on [clojure/tools.logging](https://github.com/clojure/tools.logging)
  * Namespace based loggers: `trace`, `debug`, `info`, `warn`, `error`, `fatal`
* Mapped Diagnostic Context (MDC) support via [SLF4j](http://www.slf4j.org/)
* Support for defining custom loggers
