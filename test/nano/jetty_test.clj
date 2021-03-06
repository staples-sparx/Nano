(ns nano.jetty-test
  (:use [clojure.test])
  (:require [nano.jetty :as jetty])
  (:import (java.net URL HttpURLConnection ServerSocket)
           (org.eclipse.jetty.server Server)))

(set! *warn-on-reflection* true)

(def ^:private parsed-request (atom nil))

(defn find-open-port []
  (let [port (rand-nth (range 60000 65535))
        ^ServerSocket ss (try
                           (ServerSocket. port)
                           (catch Exception e nil))]
    (if ss
      (do (.close ss) port)
      (find-open-port))))

(defn- post-request! [url-string body]
  (let [^URL url (URL. url-string)
        connection (doto ^HttpURLConnection (.openConnection url)
                     (.setRequestMethod "GET")
                     (.setDoOutput true))]
    (doto (.getOutputStream connection)
      (spit body))
    {:status (.getResponseCode connection)
     :headers (into {} (.getHeaderFields connection))
     :body (slurp (.getInputStream connection))}))

(defn- get-request! [url-string]
  (let [^URL url (URL. url-string)
        connection (doto ^HttpURLConnection (.openConnection url)
                     (.setRequestMethod "GET"))]
    {:status (.getResponseCode connection)
     :headers (into {} (.getHeaderFields connection))
     :body (slurp (.getInputStream connection))}))

(defn test-handler [request]
  (let [request-with-body (update-in request [:body] slurp)]
    (reset! parsed-request request-with-body )
    {:status 200
     :body ""
     :headers {"Content-Type" "text/plain"}}))

(deftest create-jetty
  (let [port (find-open-port)
        ^Server server (jetty/create-jetty test-handler
                                           :port port)]
    (.start server)
    (try
      (testing "Get request with no parameters"
        (get-request! (str "http://localhost:" port "/route"))
        (is (= {:remote-addr "127.0.0.1"
                :uri "/route"
                :query-params nil
                :request-method :get
                :body ""}
               @parsed-request)))
      (testing "Get request with parameter string"
        (get-request! (str "http://localhost:"
                           port
                           "/route?string"))
        (is (= {:remote-addr "127.0.0.1"
                :uri "/route"
                :query-params "string"
                :request-method :get
                :body ""}
               @parsed-request)))
      (testing "Get request with parameters"
        (get-request! (str "http://localhost:"
                           port
                           "/route?first%2Dname=arthur&last%2Dname=dent"))
        (is (= {:remote-addr "127.0.0.1"
                :uri "/route"
                :query-params {"first-name" "arthur" "last-name" "dent"}
                :request-method :get
                :body ""}
               @parsed-request)))
      (testing "Post request with parameters"
        (post-request! (str "http://localhost:"
                           port
                           "/route?first%2Dname=arthur&last%2Dname=dent")
                       "")
        (is (= {:remote-addr "127.0.0.1"
                :uri "/route"
                :query-params {"first-name" "arthur" "last-name" "dent"}
                :request-method :post
                :body ""}
               @parsed-request)))
      (testing "Post request with parameters"
        (post-request! (str "http://localhost:" port "/route")
                       "{\"key\": \"value, \"key2\": \"value2\"}")
        (is (= {:remote-addr "127.0.0.1"
                :uri "/route"
                :query-params nil
                :request-method :post
                :body "{\"key\": \"value, \"key2\": \"value2\"}"}
               @parsed-request)))
      (finally
        (.stop server)))))
