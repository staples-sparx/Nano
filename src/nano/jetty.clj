(ns nano.jetty
  (:require [ring.util.servlet :as ring-servlet]
            [ring.util.codec :as codec])
  (:import (javax.servlet.http HttpServletResponse HttpServletRequest)
           (org.eclipse.jetty.server Server ServerConnector Connector Request)
           (org.eclipse.jetty.server.handler AbstractHandler)))

(set! *warn-on-reflection* true)

(defn request-map [^HttpServletRequest request]
  {:remote-addr (.getRemoteAddr request)
   :uri (.getRequestURI request)
   :query-params (codec/form-decode (.getQueryString request))
   :request-method (keyword (.toLowerCase (.getMethod request)))
   :body (.getInputStream request)})

(defn- build-handler [clojure-handler]
  (proxy [AbstractHandler] []
    (handle [_ ^Request base-request request response]
      (try
        (let [clojure-reponse (clojure-handler (request-map request))]
          (ring-servlet/update-servlet-response response clojure-reponse)
          (.setHandled base-request true))
        (catch Exception e
          )))))

(defn run-jetty [handler & {:keys [port] :or {port 80}}]
  (let [server (Server.)
        connector (doto ^Connector (ServerConnector. server) (.setPort port))
        jetty-handler (build-handler handler)]
    (doto server
      (.addConnector connector)
      (.setHandler jetty-handler))))