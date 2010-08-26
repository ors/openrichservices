(ns ^{:doc "Utility functions"
      :author "David Edgar Liebke"}
  rich-services.util)

(defmacro with-timeout
  "A macro that throws a java.util.concurrent.TimeoutException if the body
  is evaluated before the timeout period (specified in millisecs)."
  [ms & body]
  `(let [f# (future ~@body)]
     (.get f# ~ms java.util.concurrent.TimeUnit/MILLISECONDS)))

(defn map-get
  "Retrieves values from maps whether key is string or keyword"
  [m k]
  (let [v (cond
	   (keyword? k) (or (get m k) (get m (name k)))
	   (string? k) (or (get m k) (get m (keyword k)))
	   :else (get m k))]
    v))

(defn get-rs-param [req param-name]
  (-> req (map-get :rs-message) (map-get param-name)))

(defn get-rs-param-as-int [req param-name]
  (let [param-val (-> req (map-get :rs-message) (map-get param-name))]
    (if (string? param-val)
      (Integer/parseInt param-val)
      param-val)))

(defn merge-rs-message-responses [& requests]
  (let [bodies (map :rs-message requests)
	responses (vec (map #(map-get % :response) bodies))]
    responses))

(defn get-properties [propfile-name]
  (into {} (doto (java.util.Properties.)
	     (.load (-> (Thread/currentThread)
			.getContextClassLoader
			(.getResourceAsStream propfile-name))))))


