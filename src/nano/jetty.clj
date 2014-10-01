(ns nano.jetty
  (:require [ring.util.servlet :as ring-servlet]
            [ring.util.codec :as codec])
  (:import (javax.servlet.http HttpServletResponse HttpServletRequest)
           (org.eclipse.jetty.server Server ServerConnector Connector Request)
           (org.eclipse.jetty.server.handler AbstractHandler)))

(set! *warn-on-reflection* true)

(defn- server-error [exception]
  {:status 500
   :body (str "Server Error # " exception)})

(defn- request-map [^HttpServletRequest request]
  {:remote-addr (.getRemoteAddr request)
   :uri (.getRequestURI request)
   :query-params (some-> (.getQueryString request) (codec/form-decode "UTF-8"))
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

(defn run-jetty
  [handler & {:keys [port exception-handler]
              :or {port 80 exception-handler server-error}}]
  (let [server (Server.)
        connector (doto ^Connector (ServerConnector. server) (.setPort port))
        jetty-handler (build-handler handler exception-handler)]
    (doto server
      (.addConnector connector)
      (.setHandler jetty-handler))))