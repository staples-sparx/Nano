(ns nano.data-model-test
  (:use [clojure.test])
  (:require [cheshire.core :as json]
            [clj-http.client :as http]
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

(def data {:sku (dm/reloadable-data-model {} put-fn validate)})

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

(defn- read-body [{:keys [request-method body] :as r}]
  (if (identical? :post request-method)
    (assoc r :body (json/parse-string (slurp body) true))
    r))

(defn- fixtures [f]
  (let [handler (api/generate-handler "/data/" data)
        ^Server server (jetty/create-jetty
                         (fn [request]
                           (-> request
                               read-body
                               handler
                               (update-in [:body] json/generate-string)))
                         :port current-port
                         :acceptors 1
                         :exception-handler test-exception-handler)]
    (dm/set-data (:sku data) {"1234" {:price 10 :cog 4}})
    (binding [*out* (clojure.java.io/writer (StringWriter.))]
      (.start server))
    (try (f)
         (catch Exception e (.printStackTrace e))
         (finally (.stop server)))))

(defn- client-exception-handler [e]
  (println (str (.printStackTrace ^Throwable e))))

(use-fixtures :once fixtures)

(deftest get-routes
  (let [route (format "http://localhost:%s/data/" current-port)]
    (is (= {:sku {:reloadable? true}}
           (:body (http/get route {:as :json}))))
    (is (= (dm/get-data (:sku data))
           (into {} (map (fn [[k v]] [(name k) v])
                         (:body (http/get (str route "sku") {:as :json}))))))
    (is (= (keys (dm/get-data (:sku data)))
           (:body (http/get (str route "sku/") {:as :json-strict-string-keys}))))))

(deftest reload-test
  (let [route (format "http://localhost:%s/data/" current-port)]
    (testing "Load"
      (is (= :success (client/load-data
                        route :sku {"2345" {:price 1 :cog 3}
                                    "3456" {:price 2 :cog 4}})))
      (is (= (dm/get-data (:sku data)) {"2345" {:price 1 :cog 3}
                                        "3456" {:price 2 :cog 4}})))
    (testing "Incremental load"
      (is (= :success (client/incremental-load-data
                        route :sku [{"2345" {:price 1 :cog 3}}
                                    {"3456" {:price 2 :cog 4}}]
                        :serialize-fn json/generate-string
                        :deserialize-fn #(json/parse-string % true)
                        :exception-handler client-exception-handler)))
      (is (= (dm/get-data (:sku data)) {"2345" {:price 1 :cog 3}
                                        "3456" {:price 2 :cog 4}})))))