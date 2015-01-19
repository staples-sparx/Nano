(ns nano.data.data-collection
  (:require [nano.data.data-model :as dm]))

(set! *warn-on-reflection* true)

(defn get-data [coll data-model-key]
  (if-let [data-model (get coll data-model-key)]
    (dm/get-data data-model)
    :unknown-data-model))

(defn get-in-data [coll data-model-key keys]
  (if-let [data-model (get coll data-model-key)]
    (dm/get-in-data data-model keys)
    :unknown-data-model))

(defn put-data [coll data-model-key data]
  (if-let [data-model (get coll data-model-key)]
    (if (dm/reloadable? data-model)
      (if (dm/valid-data? data-model data)
        (if (dm/incremental? data-model)
          (if (dm/reload-started? data-model)
            (dm/put-data data-model data)
            :reload-not-started)
          (dm/set-data data-model data))
        :validate-failed)
      :model-cannot-be-set)
    :unknown-data-model))

(defn init-reload [coll data-model-key]
  (if-let [data-model (get coll data-model-key)]
    (if (dm/incremental? data-model)
      (dm/init-reload data-model)
      :not-incremental)
    :unknown-data-model))

(defn cancel-reload [coll data-model-key]
  (if-let [data-model (get coll data-model-key)]
    (if (dm/incremental? data-model)
      (if (dm/reload-started? data-model)
        (dm/cancel-reload data-model)
        :reload-not-in-progress)
      :not-incremental)
    :unknown-data-model))

(defn complete-reload [coll data-model-key]
  (if-let [data-model (get coll data-model-key)]
    (if (dm/incremental? data-model)
      (if (dm/reload-started? data-model)
        (dm/complete-reload data-model)
        :reload-not-in-progress)
      :not-incremental)
    :unknown-data-model))
