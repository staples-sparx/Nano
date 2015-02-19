(ns nano.data.data-model)

(defprotocol Settable
  (reloadable? [this])
  (get-data [this])
  (get-in-data [this ks])
  (set-data [this data])
  (valid-data? [this data]))

(defprotocol Reloadable
  (put-data [this chunk-number data final-chunk?]))

(deftype SetOnceDataModel [live-state]
  Settable
  (get-data [_] @live-state)
  (get-in-data [_ ks] (get-in @live-state ks))
  (set-data [_ data] (reset! live-state data) nil)
  (reloadable? [_] false)
  (valid-data? [_ _] true))

(deftype ReloadableDataModel
  [empty-collection
   live-state
   reload-state
   put-fn
   validate-fn
   chunks-received
   reload-callback]
  Reloadable
  (put-data [_ chunk-number data final-chunk?]
    (when (== chunk-number 0)
      (reset! reload-state empty-collection))
    (swap! chunks-received conj chunk-number)
    (swap! reload-state put-fn data)
    (if final-chunk?
      (let [missing (filter #(not (contains? @chunks-received %))
                            (range (inc chunk-number)))]
        (if (empty? missing)
          (do (reset! live-state @reload-state)
              (reset! reload-state nil)
              (reset! chunks-received #{})
              (when reload-callback (reload-callback))
              {:chunk-number chunk-number})
          {:chunk-number chunk-number
           :missing-chunks missing}))
      {:chunk-number chunk-number}))
  Settable
  (get-data [_] @live-state)
  (get-in-data [_ ks] (get-in @live-state ks))
  (set-data [_ data] (reset! live-state data))
  (reloadable? [_] true)
  (valid-data? [_ data] (validate-fn data)))

(defn reloadable-data-model
  ([] (reloadable-data-model nil (fn [_ data] data) (fn [_] true)))
  ([empty-collection put-fn validate-fn]
    (reloadable-data-model empty-collection put-fn validate-fn nil))
  ([empty-collection put-fn validate-fn reload-callback]
    (->ReloadableDataModel empty-collection
                           (atom nil)
                           (atom nil)
                           put-fn
                           validate-fn
                           (atom #{})
                           reload-callback)))

(defn set-once-data-model []
  (->SetOnceDataModel (atom nil)))