# Nano

A Clojure library that implements an embedded Jetty 9 server and handler.

This library is pre-alpha. The api has not been finalized. Breaking changes are possible.

## Usage

```clj
;; In your ns statement:
(ns my.ns
  (:require [nano.jetty :as jetty]))
```

### Start Server

```clj
;; To start server and join thread
(jetty/run-jetty
  #'your-handler
  :port 8080
  :max-threads 150
  :min-threads 50
  :acceptors -1
  :selectors -1
  :exception-handler #'your-exception-handler)

;; To return Server object that you must call .start on.
(jetty/create-jetty
  #'your-handler
  :port 8080
  :max-threads 150
  :min-threads 50
  :acceptors -1
  :selectors -1
  :exception-handler #'your-exception-handler)
```


## License

```clj
:license {:name "MIT License"
          :url "http://mit-license.org/"}
```