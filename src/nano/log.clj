(ns nano.log)

(defn- value-with-times
  [thunk]
  (let [start (System/currentTimeMillis)
        start-us (/ (System/nanoTime) 1000)
        value (thunk)
        end-us (/ (System/nanoTime) 1000)]
    [value start (System/currentTimeMillis) (- end-us start-us)]))

(defmacro wrap-with-logging [log-fn request & body]
  `(let [[reply# start# end# elapsed#] (value-with-times (fn [] ~@body))]
     (~log-fn {:request ~request
               :reply reply#
               :meta {:start start# :end end# :elapsed elapsed#}})
     reply#))