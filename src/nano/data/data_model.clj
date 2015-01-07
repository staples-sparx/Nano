(ns nano.data.data-model)

(defprotocol Settable
  (reloadable? [this])
  (get-data [this])
  (get-in-data [this ks])
  (set-data [this data])
  (incremental? [this])
  (valid-data? [this data]))

(defprotocol IncrementalSettable
  (init-reload [this])
  (complete-reload [this])
  (put-data [this data])
  (reload-started? [this]))

(deftype ReloadableDataModel [live-state set-fn validate-fn]
  Settable
  (get-data [_] @live-state)
  (get-in-data [_ ks] (get-in @live-state ks))
  (set-data [_ data] (swap! live-state set-fn data))
  (reloadable? [_] true)
  (incremental? [_] false)
  (valid-data? [_ data] (validate-fn data)))

(deftype SetOnceDataModel [live-state]
  Settable
  (get-data [_] @live-state)
  (get-in-data [_ ks] (get-in @live-state ks))
  (set-data [_ data] (reset! live-state data))
  (reloadable? [_] false)
  (incremental? [_] false)
  (valid-data? [_ _] true))

(deftype IncrementalReloadableDataModel
  [empty-collection live-state reload-state put-fn validate-fn reload-started?]
  IncrementalSettable
  (init-reload [_]
    (reset! reload-started? true)
    (reset! reload-state empty-collection))
  (complete-reload [_]
    (reset! live-state @reload-state)
    (reset! reload-state nil)
    (reset! reload-started? false))
  (put-data [_ data] (swap! reload-state put-fn data))
  (reload-started? [_] @reload-started?)
  Settable
  (get-data [_] @live-state)
  (get-in-data [_ ks] (get-in @live-state ks))
  (set-data [_ data] (reset! live-state data))
  (reloadable? [_] true)
  (incremental? [_] true)
  (valid-data? [_ data] (validate-fn data)))

(defn incremental-reloadable-data-model [empty-collection put-fn validate-fn]
  (->IncrementalReloadableDataModel empty-collection
                                    (atom nil)
                                    (atom nil)
                                    put-fn
                                    validate-fn
                                    (atom false)))

(defn reloadable-data-model
  ([] (reloadable-data-model (fn [_ data] data) (fn [_] true)))
  ([set-fn validate-fn]
    (->ReloadableDataModel (atom nil) set-fn validate-fn)))

(defn set-once-data-model []
  (->SetOnceDataModel (atom nil)))