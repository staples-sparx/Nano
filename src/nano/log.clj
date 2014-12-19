(ns nano.log)

(defmacro wrap-with-logging [log-fn request & body]
  `(let [start# (System/currentTimeMillis)
         start-nano# (System/nanoTime)
         reply# ~@body
         end# (System/currentTimeMillis)
         elapsed# (/ (- (System/nanoTime) start-nano#) 1000)]
     (~log-fn {:request ~request
               :reply reply#
               :meta {:start (/ start# 1000000)
                      :end (/ end# 1000000)
                      :elapsed elapsed#}})
     reply#))