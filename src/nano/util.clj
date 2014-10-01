(ns nano.util
  (:import (java.net URLDecoder)))

(defn assoc-conj
  [map k v]
  (assoc! map k
          (if-let [cur (get map k)]
            (if (vector? cur)
              (conj cur v)
              [cur v])
            v)))

(defn decode-string
  [^String encoded encoding]
  (try
    (URLDecoder/decode encoded encoding)
    (catch Exception e nil)))

(defn decode-params
  [^String encoded encoding]
  (if-not (.contains encoded "=")
    (decode-string encoded encoding)
    (persistent!
      (reduce
        (fn [m param]
          (if-let [[k v] (.split param "=" 2)]
            (assoc-conj m (decode-string k encoding) (decode-string v encoding))
            m))
        (transient {})
        (.split encoded "&")))))