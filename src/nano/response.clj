(ns nano.response)

(defn not-found [url]
  {:status 404
   :body (str "The requested URL does not exist: " url)
   :content-type "text/plain"})

(defn method-not-supported [method]
  {:status 405
   :body (str "The request method is not supported: " (name (or method "nil")))
   :content-type "text/plain"})
