(ns nano.log)

(defmacro wrap-with-logging [log-fn request & body]
  `(let [start# (System/currentTimeMillis)
         start-us# (/ (System/nanoTime) 1000)
         reply# ~@body
         end# (System/currentTimeMillis)
         elapsed# (/ (- (System/nanoTime) start-us#) 1000)]
     (~log-fn {:request ~request
               :reply reply#
               :meta {:start (/ start# 1000000)
                      :end (/ end# 1000000)
                      :elapsed elapsed#}})
     reply#))