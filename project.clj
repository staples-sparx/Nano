(defproject org.sparx/nano "0.6.1-SNAPSHOT"
  :description "A lightweight handler for Jetty 9"
  :url "https://github.com/staples-sparx/Nano"
  :license {:name "MIT License"
            :url "http://mit-license.org/"}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]
                                  [cheshire "5.4.0"]]}}
  :dependencies [[org.eclipse.jetty/jetty-server "9.3.6.v20151106"]
                 [org.eclipse.jetty.http2/http2-server "9.3.6.v20151106"]
                 [ring/ring-servlet "1.4.0"]
                 [bidi "1.12.0"]
                 [clj-http "1.0.1"]]
  :exclusions [org.clojure/clojure])
