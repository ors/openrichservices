(ns ^{:doc "A simple Rich-Service instance with a few basic and composed app-services."}
  examples.services
  (:use rich-services.adl))

(defn hello-world-service [message]
  (str "Hello " (get-resp-or-param message "name" "world")))

(defn to-upper-service [message]
  (.toUpperCase (get-resp-or-param message "value" "to-upper")))

(defn to-lower-service [message]
  (.toLowerCase (get-resp-or-param message "value" "to-lower")))

(defn interpose-service [message]
  (apply str (interpose (get-param message "sep" "-")
			(get-resp-or-param message "value" "interpose"))))

(defn logging-service [message]
  (let [msg (str "Logged: " (get-param message :uri "Nothing to log."))]
    (println (str "Log: " msg))
    (str "Log: " msg)))

(defn reduce-service [message]
  (let [map-result (get-response message)]
    (if (coll? map-result)
      (apply str (interpose ":" (sort map-result)))
      map-result)))

(defn long-running-service [message]
  (let [sleep (Long/parseLong (get-param message "sleep" "1000"))]
    (println (str "Long running service about to sleep for " sleep " milliseconds..."))
    (Thread/sleep sleep)
    (println "Long running services complete.")
    (str "long-running-service result slept for " sleep " milliseconds.")))

(defn echo-service [message]
  (get-resp-or-param message :value))

