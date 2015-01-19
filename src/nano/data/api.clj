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

(defn- reload-not-started [data-key]
  (error-response (str "Reload process has not started for \""
                       (name data-key)
                       "\".")))

(defn- model-not-reloadable [data-key]
  (error-response (str "Data model \"" (name data-key) "\" cannot be reloaded.")))

(defn- unknown-model-response [data-key]
  (error-response (str "Data model \"" (name data-key) "\" not recognized.")))

(defn- not-incremental [data-key]
  (error-response (str "Data model \""
                       (name data-key)
                       "\" does not support incremental reload.")))

(defn not-in-progress [data-key]
  (error-response (format "Data model \"%s\" is not in process of being reloaded."
                          (name data-key))))

(defn not-associative [data-key]
  (error-response (format "Data model \"%s\" is not associative at this level."
                          (name data-key))))

(defn- response [data-key dm-response success-fn]
  (case dm-response
    :unknown-data-model (unknown-model-response data-key)
    :model-cannot-be-set (model-not-reloadable data-key)
    :reload-not-started (reload-not-started data-key)
    :validate-failed (error-response "Data did not pass the validation step.")
    :not-incremental (not-incremental data-key)
    :reload-not-in-progress (not-in-progress data-key)
    :not-associative (not-associative data-key)
    (success-fn dm-response)))

(defn- init-reload [key->data-model method data-key]
  (let [data-key (keyword data-key)]
    (if (= method :post)
      (response data-key
                (dc/init-reload key->data-model data-key)
                (fn [_]
                  (success-response
                    (format "Data model \"%s\" ready to be loaded."
                            (name data-key)))))
      (response/method-not-supported method))))

(defn- cancel-reload [key->data-model method data-key]
  (let [data-key (keyword data-key)]
    (if (= method :post)
      (response data-key
                (dc/cancel-reload key->data-model data-key)
                (fn [_]
                  (success-response
                    (format "Data model \"%s\" ready to be loaded."
                            (name data-key)))))
      (response/method-not-supported method))))

(defn complete-reload [key->data-model method data-key]
  (let [data-key (keyword data-key)]
    (if (= method :post)
      (response data-key
                (dc/complete-reload key->data-model data-key)
                (fn [_] (success-response
                          (format "Data model \"%s\" loaded."
                                  (name data-key)))))
      (response/method-not-supported method))))

(defn- list-data [key->data-model method]
  (if (= method :get)
    (success-response (build-dm-documentation key->data-model))
    (response/method-not-supported method)))

(defn- data [key->data-model method data-key body]
  (case method
    :get (response data-key (dc/get-data key->data-model data-key) success-response)
    :post (response data-key (dc/put-data key->data-model data-key body)
                    (fn [data] (success-response "Data Reloaded.")))
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
        list-keys (partial list-keys key->data-model)
        init-reload (partial init-reload key->data-model)
        cancel-reload (partial cancel-reload key->data-model)
        complete-reload (partial complete-reload key->data-model)]
    (fn [{:keys [handler route-params]} {:keys [request-method body query-params]}]
      (let [data-key (:data-key route-params)
            response (case handler
                       :list-data (list-data request-method)
                       :data (data request-method data-key body)
                       :list-keys (list-keys request-method data-key (get-key-vector route-params))
                       :init-reload (init-reload request-method (get query-params "data-key"))
                       :complete-reload (complete-reload request-method (get query-params "data-key"))
                       :cancel-reload (cancel-reload request-method (get query-params "data-key"))
                       :data-value (data-value request-method
                                               data-key
                                               (get-key-vector route-params)))]
        response))))

(defn generate-handler [route-prefix key->data-model]
  (let [dispatcher (build-dispatcher key->data-model)
        data-routes
        (bidi/compile-route
          [route-prefix {"" :list-data
                         "init-reload" :init-reload
                         "complete-reload" :complete-reload
                         "cancel-reload" :cancel-reload
                         [[keyword :data-key]] {"" :data
                                                "/" {"" :list-keys
                                                     [:key] {"" :data-value
                                                             "/" {"" :list-keys
                                                                  [:key2] :data-value}}}}}])]
    (fn [{:keys [uri] :as request}]
      (when-let [route-map (bidi/match-route data-routes uri)]
        (dispatcher route-map request)))))