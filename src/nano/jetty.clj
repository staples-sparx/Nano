(ns nano.jetty
  (:require [ring.util.servlet :as ring-servlet]
            [nano.util :as util])
  (:import (javax.servlet.http HttpServletResponse HttpServletRequest)
           (org.eclipse.jetty.server Server ServerConnector Connector Request)
           (org.eclipse.jetty.server.handler AbstractHandler)
           (org.eclipse.jetty.util.thread QueuedThreadPool)))

(set! *warn-on-reflection* true)

(defn- server-error [exception]
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
      (let [clojure-response (try
                               (clojure-handler (request-map request))
                               (catch Exception e
                                 (exception-handler e)))]
        (ring-servlet/update-servlet-response response clojure-response)
        (.setHandled base-request true)))))

(defn create-jetty
  [handler & {:keys [port exception-handler max-threads min-threads]
              :or {port 80
                   exception-handler server-error
                   max-threads 150
                   min-threads 50}}]
  (let [^QueuedThreadPool thread-pool (QueuedThreadPool. max-threads min-threads)
        server (Server. thread-pool)
        connector (doto ^Connector (ServerConnector. server) (.setPort port))
        jetty-handler (build-handler handler exception-handler)]
    (doto server
      (.addConnector connector)
      (.setHandler jetty-handler))))

(defn run-jetty
  [handler & options]
  (doto ^Server (apply create-jetty handler options)
    (.start)
    (.join)))