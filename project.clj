(defproject nano "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]]}}
  :dependencies [[org.eclipse.jetty/jetty-server "9.2.3.v20140905"]
                 [ring/ring-servlet "1.3.1"]]
  :exclusions [org.clojure/clojure])
