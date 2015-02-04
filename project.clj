(defproject org.sparx/nano "0.5.0"
  :description "A lightweight handler for Jetty 9"
  :url "https://github.com/staples-sparx/Nano"
  :license {:name "MIT License"
            :url "http://mit-license.org/"}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]]}}
  :dependencies [[org.eclipse.jetty/jetty-server "9.2.3.v20140905"]
                 [ring/ring-servlet "1.3.1"]
                 [bidi "1.12.0"]
                 [clj-http "1.0.1"]]
  :exclusions [org.clojure/clojure])
