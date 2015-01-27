(ns nano.data.api
  (:require [bidi.bidi :as bidi]
            [nano.data.data-collection :as dc]
            [nano.data.data-model :as dm]
            [nano.response :as response]))


(defn- get-key-vector [route-params]
  (vec (remove nil? (vals (select-keys route-params [:key :key2])))))

(defn- build-dm-documentation [name->dm]
  (into {} (map (fn [[k v]]
                  [k {:reloadable? (dm/reloadable? v)
                      :incremental? (dm/incremental? v)}])
                name->dm)))

(defn- success-response [body]
  {:status 200
   :body body
   :content-type "application/json"})

(defn- error-response [body]
  {:status 400
   :body body
   :content-type "application/json"})

(defn- model-not-reloadable [data-key]
  (error-response {:message (format "Data model \"%s\" cannot be reloaded."
                                    (name data-key))}))

(defn- unknown-model-response [data-key]
  (error-response {:message (format "Data model \"%s\" not recognized."
                                    (name data-key))}))

(defn- not-associative [data-key]
  (error-response {:message (format "Data model \"%s\" is not associative at this level."
                                    (name data-key))}))

(defn- load-success-fn [{:keys [chunk-number missing-chunks]}]
  (success-response
    (cond-> {:message "Reloaded data model."}
            chunk-number (assoc :message (format "Received chunk number %s."
                                                 chunk-number))
            missing-chunks (assoc :missing-chunks missing-chunks))))

(defn- response [data-key dm-response success-fn]
  (case dm-response
    :unknown-data-model (unknown-model-response data-key)
    :model-cannot-be-set (model-not-reloadable data-key)
    :validate-failed (error-response "Data did not pass the validation step.")
    :not-associative (not-associative data-key)
    (success-fn dm-response)))

(defn- list-data [key->data-model method]
  (if (= method :get)
    (success-response (build-dm-documentation key->data-model))
    (response/method-not-supported method)))

(defn- data [key->data-model method data-key body]
  (case method
    :get (response data-key (dc/get-data key->data-model data-key) success-response)
    :post (response data-key
                    (dc/put-data key->data-model data-key body)
                    load-success-fn)
    (response/method-not-supported method)))

(defn- list-keys [key->data-model method data-key keys]
  (if (= method :get)
    (response data-key
              (try (vec (clojure.core/keys (dc/get-in-data key->data-model
                                                           data-key
                                                           keys)))
                   (catch ClassCastException _ :not-associative))
              success-response)
    (response/method-not-supported method)))

(defn- data-value [key->data-model method data-key keys]
  (if (= method :get)
    (response data-key (dc/get-in-data key->data-model data-key keys) success-response)
    (response/method-not-supported method)))

(defn- build-dispatcher [key->data-model]
  (let [list-data (partial list-data key->data-model)
        data (partial data key->data-model)
        data-value (partial data-value key->data-model)
        list-keys (partial list-keys key->data-model)]
    (fn [{:keys [handler route-params]} {:keys [request-method body]}]
      (let [data-key (:data-key route-params)
            response (case handler
                       :list-data (list-data request-method)
                       :data (data request-method data-key body)
                       :list-keys (list-keys request-method data-key (get-key-vector route-params))
                       :data-value (data-value request-method
                                               data-key
                                               (get-key-vector route-params)))]
        response))))

(defn generate-handler [route-prefix key->data-model]
  (let [dispatcher (build-dispatcher key->data-model)
        data-routes
        (bidi/compile-route
          [route-prefix {"" :list-data
                         [[keyword :data-key]] {"" :data
                                                "/" {"" :list-keys
                                                     [:key] {"" :data-value
                                                             "/" {"" :list-keys
                                                                  [:key2] :data-value}}}}}])]
    (fn [{:keys [uri] :as request}]
      (when-let [route-map (bidi/match-route data-routes uri)]
        (dispatcher route-map request)))))