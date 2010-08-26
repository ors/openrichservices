(ns ^{:doc "Prototype Rich-Services Infrastructure: app and infrastructure services functions"
      :author "David Edgar Liebke"}
  rich-services.services
  (:use [clojure.contrib.json :only (json-str)]
	[rich-services.util :only [get-rs-param]]
	[clojure.contrib.logging :only (debug)])
  (:require [rich-services.proxy :as proxy]
	    [rich-services.config :as config]
	    [clojure.string :as s]))

(def *base-url* (ref "http://localhost:8080"))

(def *local-app-services* (ref {}))

(def *global-app-services* (ref {}))

(defn base-url
  ([] @*base-url*)
  ([url] (dosync (ref-set *base-url* url))))

(defn instance-and-service [uri]
  (cond
   (string? uri)
    (let [name-vals (when uri (s/split uri #"/"))]
     (condp = (count name-vals)
	 3 (rest name-vals)
	 2 (conj (second name-vals) nil)
	 nil))
    (keyword? uri) [(namespace uri) (name uri)]
    :else [nil nil]))

(defn register-app-service
  ([uri] (register-app-service uri (base-url)))
  ([uri location]
     (when (config/slave-mode?)
       (debug (str "Registering with master registrar (" (config/get-master-registrar) "): " uri))
       (proxy/get-request (str (config/get-master-registrar) "/registrar/register")
			  {"service-uri" uri "service-location" location}))
     (debug (str "Registering with local registrar: " uri))
     (dosync (alter *global-app-services* assoc uri location))))

(defn unregister-app-service [uri base-url]
  (dosync (alter *global-app-services* dissoc uri base-url)))

(defn register-local-app-service [uri f]
  (dosync (alter *local-app-services* assoc uri f)))

