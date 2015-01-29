(ns nano.data-model-test
  (:use [clojure.test])
  (:require [clojure.data.json :as json]
            [nano.data.client :as client]
            [nano.data.data-model :as dm]
            [nano.data.api :as api]
            [nano.jetty :as jetty])
  (:import (java.net ServerSocket)
           (org.eclipse.jetty.server Server)
           (java.io StringWriter)))

(set! *warn-on-reflection* true)

(defn- validate [data]
  (every? (fn [[k v]] (and (contains? v :price)
                           (contains? v :cog))) data))

(defn put-fn [data-model data]
  (merge data-model (into {} (map (fn [[k v]] [(name k) v]) data))))

(def data {:sku (dm/incremental-reloadable-data-model {} put-fn validate)})

(defn- open-port []
  (let [port (rand-nth (range 60000 65535))
        ss (try
             (ServerSocket. port)
             (catch Exception e nil))]
    (if ss
      (do (.close ^ServerSocket ss) port)
      (open-port))))

(defonce current-port (open-port))

(defn- test-exception-handler [_ ^Throwable e]
  (.printStackTrace e))

(defn- read-body [body]
  (let [new-body (json/read-json (slurp body))]
    new-body))

(defn- fixtures [f]
  (let [handler (api/generate-handler "/data/" data)
        ^Server server (jetty/create-jetty
                         (fn [request]
                           (-> request
                               (update-in [:body] read-body)
                               handler
                               (update-in [:body] json/write-str)))
                         :port current-port
                         :acceptors 1
                         :exception-handler test-exception-handler)]
    (dm/set-data (:sku data) {"1234" {"price" 10 "cog" 4}})
    (binding [*out* (clojure.java.io/writer (StringWriter.))]
      (.start server))
    (try (f)
         (catch Exception e (.printStackTrace e))
         (finally (.stop server)))))

(use-fixtures :once fixtures)

(deftest incremental-reload-test
  (testing ""
    (let [route (format "http://localhost:%s/data/" current-port)]
      (is (= :success (client/incremental-load-data
                        route :sku [{"2345" {:price 1 :cog 3}}
                                    {"3456" {:price 2 :cog 4}}])))
      (is (= (dm/get-data (:sku data)) {"2345" {:price 1 :cog 3}
                                        "3456" {:price 2 :cog 4}})))))