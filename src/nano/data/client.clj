(ns nano.data.client
  (:require [clj-http.client :as client])
  (:import (java.util.concurrent ExecutorService Executors)))

(defonce ^:private threadpool (Executors/newCachedThreadPool))

(defn- submit-fn [request-fn]
  (.submit ^ExecutorService threadpool
           ^Callable (cast Callable (request-fn))))

(defn- try-request [url body deserialize-fn]
  (try
    (let [response (-> (client/post url {:body body})
                       :body
                       (deserialize-fn true))]
      response)
    (catch Exception e
      (println e)
      nil)))

(defn try-request-repeatedly [url body pred deserialize-fn]
  (loop [i 0]
    (if (== i 5)
      nil
      (let [response (try-request url body deserialize-fn)]
        (if (pred response)
          response
          (do (Thread/sleep 500)
              (recur (inc i))))))))

(defn- request-fn [url chunk-num datum serialize-fn deserialize-fn]
  (fn []
    (try-request
      url
      (serialize-fn {:chunk-number chunk-num :data datum})
      deserialize-fn)))

(defn- send-chunks
  [url chunks indices serialize-fn deserialize-fn attempt-number]
  (loop [indices indices attempt attempt-number]
    (if (== 5 attempt)
      :failure/attempts-exhausted
      (let [fs (for [i (butlast indices)
                     :let [chunk (nth chunks i)]]
                 (submit-fn (request-fn url i chunk serialize-fn deserialize-fn)))
            final-chunk-num (last indices)
            final-chunk (nth chunks final-chunk-num)]
        (doseq [f fs] (deref f))
        (if-let [response (try-request-repeatedly
                            url (serialize-fn {:data final-chunk
                                               :chunk-number final-chunk-num
                                               :final-chunk? true})
                            (fn [response] (= (:message response)
                                              (format "Received chunk number %s."
                                                      final-chunk-num)))
                            deserialize-fn)]
          (if (not (empty? (:missing-chunks response)))
            (recur (:missing-chunks response) (inc attempt))
            :success)
          :failure/complete-reload)))))

(defn- incremental-load-data [ip port key chunk-vec serialize-fn deserialize-fn]
  (let [url (str "http://" ip ":" port "/data/" (name key))
        one-chunk? (== (count chunk-vec) 1)
        indices (range (count chunk-vec))]
    (if-not (try-request-repeatedly
              url
              (serialize-fn {:chunk-number 0
                             :data (first chunk-vec)
                             :final-chunk? one-chunk?})
              (fn [response] (= response {:message "Received chunk number 0."}))
              deserialize-fn)
      :failure/init-reload
      (if one-chunk? :success (send-chunks url chunk-vec (rest indices) serialize-fn deserialize-fn 1)))))