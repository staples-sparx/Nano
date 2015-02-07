(ns nano.data.client
  (:require [clj-http.client :as client])
  (:import (java.util.concurrent ExecutorService Executors)))

(defonce ^:private default-threadpool (Executors/newCachedThreadPool))

(defn- submit-fn [request-fn {:keys [threadpool]}]
  (.submit ^ExecutorService (or threadpool default-threadpool)
           ^Callable (cast Callable request-fn)))

(defn- try-request
  [url body {:keys [serialize-fn deserialize-fn exception-handler]}]
  (if serialize-fn
    (try
      (let [response (-> (client/post url {:body (serialize-fn body)})
                         :body
                         deserialize-fn)]
        response)
      (catch Exception e
        (if exception-handler (exception-handler e) (println e))
        nil))
    (try
      (let [response (:body (client/post url {:form-params body
                                              :content-type :json
                                              :as :json}))]
        response)
      (catch Exception e
        (if exception-handler (exception-handler e) (println e))
        nil))))

(defn try-request-repeatedly [url body pred options]
  (loop [i 0]
    (if (== i 5)
      nil
      (let [response (try-request url body options)]
        (if (pred response)
          response
          (do (Thread/sleep 500)
              (recur (inc i))))))))

(defn- request-fn [url chunk-num datum options]
  (fn []
    (try-request
      url
      {:chunk-number chunk-num :data datum}
      options)))

(defn- send-chunks
  [url chunks indices options]
  (loop [indices indices attempt 0]
    (if (== 5 attempt)
      :failure/attempts-exhausted
      (let [fs (for [i (butlast indices)
                     :let [chunk (nth chunks i)]]
                 (submit-fn (request-fn url i chunk options) options))
            final-chunk-num (last indices)
            final-chunk (nth chunks final-chunk-num)]
        (doseq [f fs] (deref f))
        (if-let [response (try-request-repeatedly
                            url
                            {:data final-chunk
                             :chunk-number final-chunk-num
                             :final-chunk? true}
                            (fn [response] (= (:message response)
                                              (format "Received chunk number %s."
                                                      final-chunk-num)))
                            options)]
          (if (not (empty? (:missing-chunks response)))
            (recur (:missing-chunks response) (inc attempt))
            :success)
          :failure/complete-reload)))))

(defn incremental-load-data [data-url key chunk-vec & options]
  (let [url (str data-url (name key))
        one-chunk? (== (count chunk-vec) 1)
        indices (range (count chunk-vec))
        options (apply hash-map options)]
    (if-not (try-request-repeatedly
              url
              {:chunk-number 0
               :data (first chunk-vec)
               :final-chunk? one-chunk?}
              (fn [response] (= response {:message "Received chunk number 0."}))
              options)
      :failure/init-reload
      (if one-chunk? :success (send-chunks url chunk-vec (rest indices) options)))))

(defn load-data [data-url key data & options]
  (if options
    (apply incremental-load-data data-url key [data] options)
    (incremental-load-data data-url key [data])))