(ns nano.jetty
  (:require [ring.util.servlet :as ring-servlet]
            [nano.util :as util])
  (:import (javax.servlet.http HttpServletRequest)
           (org.eclipse.jetty.server Server ServerConnector
                                     Request
                                     HttpConfiguration
                                     HttpConnectionFactory
                                     ConnectionFactory)
           (org.eclipse.jetty.server.handler AbstractHandler)
           (org.eclipse.jetty.util.thread QueuedThreadPool)
           (org.eclipse.jetty.http2.server HTTP2CServerConnectionFactory)))

(set! *warn-on-reflection* true)

(defn- server-error [request exception]
  {:status 500
   :headers {"Content-Type" "text/plain"}
   :body (str "Server Error # " exception)})

(defn- request-map [^HttpServletRequest request]
  {:remote-addr (.getRemoteAddr request)
   :uri (.getRequestURI request)
   :query-params (some-> (.getQueryString request) (util/decode-params "UTF-8"))
   :request-method (keyword (.toLowerCase (.getMethod request)))
   :body (.getInputStream request)})

(defn- build-handler [clojure-handler exception-handler]
  (proxy [AbstractHandler] []
    (handle [_ ^Request base-request request response]
      (let [clojure-request (try
                              (request-map request)
                              (catch Exception e
                                (exception-handler
                                  {:ERROR "Exception while parsing request"} e)))
            clojure-response (try
                               (clojure-handler clojure-request)
                               (catch Exception e
                                 (exception-handler clojure-request e)))]
        (ring-servlet/update-servlet-response response clojure-response)
        (.setHandled base-request true)))))

(defn- build-connector [^Server server acceptors selectors port]
  (let [http-config (HttpConfiguration.)
        http1 (HttpConnectionFactory. http-config)
        http2 (HTTP2CServerConnectionFactory. http-config)
        factories (into-array ConnectionFactory [http1 http2])]
    (doto (ServerConnector.
            server
            (int acceptors)
            (int selectors)
            ^"[Lorg.eclipse.jetty.server.ConnectionFactory;" factories)
      (.setPort port))))

(defn create-jetty
  [handler & {:keys [port exception-handler max-threads min-threads ^int acceptors ^int selectors]
              :or {port 8080
                   exception-handler server-error
                   max-threads 150
                   min-threads 50
                   acceptors -1
                   selectors -1}}]
  (let [thread-pool (QueuedThreadPool. max-threads min-threads)
        server (Server. thread-pool)
        jetty-handler (build-handler handler exception-handler)
        connector (build-connector server acceptors selectors port)]
    (doto server
      (.addConnector connector)
      (.setHandler jetty-handler))))

(defn run-jetty
  [handler & options]
  (doto ^Server (apply create-jetty handler options)
    (.start)
    (.join)))
