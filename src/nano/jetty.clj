(ns nano.jetty
  (:require [ring.util.servlet :as ring-servlet])
  (:import (javax.servlet.http HttpServletResponse HttpServletRequest)
           (org.eclipse.jetty.server Server ServerConnector Connector)
           (org.eclipse.jetty.server.handler AbstractHandler)))

(set! *warn-on-reflection* true)

(defn request-map [^HttpServletRequest request]
  {:remote-addr (.getRemoteAddr request)
   :uri (.getRequestURI request)
   :query-string (.getQueryString request)
   :body (.getInputStream request)})

(defn- build-handler [clojure-handler]
  (proxy [AbstractHandler] []
    (handle [_ base-request request response]
      (try
        (let [clojure-reponse (clojure-handler (request-map request))]
          (ring-servlet/update-servlet-response base-request clojure-reponse))
        (catch Exception e
          )))))

(defn run-jetty [handler & {:keys [port] :or {port 9000}}]
  (let [server (Server.)
        connector (doto ^Connector (ServerConnector. server) (.setPort port))
        jetty-handler (build-handler handler)]
    (doto server
      (.addConnector connector)
      (.setHandler jetty-handler))))