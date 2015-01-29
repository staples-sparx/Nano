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

(defn put-data [coll data-model-key {:keys [data chunk-number final-chunk?]}]
  (if-let [data-model (get coll data-model-key)]
    (if (dm/reloadable? data-model)
      (if (dm/valid-data? data-model data)
        (dm/put-data data-model chunk-number data final-chunk?)
        :validate-failed)
      :model-cannot-be-set)
    :unknown-data-model))
