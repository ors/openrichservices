(ns rich-services.adl
  (:use [rich-services.controller :only [get-service-controller service-controller-proxy
					 to-uri]]
	[rich-services.scheduler :only [schedule-periodic]]
	[rich-services.util :only [merge-rs-message-responses map-get
				   with-timeout]])
  (:require [rich-services.security :as sec]
	    [clojure.string :as s]))

(defn get-response
  "A helper function that extracts the response value from the previous
   rich-service, if the current function is part of a composite service.
   An optional third argument provides a default value for the cases
   where there is no previous response."
  ([req] (get-response req nil))
  ([req default-value] (or (-> req (map-get :rs-message) (map-get :response))
			   default-value)))

(defn get-param
  "A helper function that extracts the named param from the request map."
  ([req param-name] (get-param req param-name nil))
  ([req param-name default-value]
     (or (-> req (map-get :rs-message) (map-get param-name))
	 default-value)))

(defn get-resp-or-param
  "A helper function that extracts either the response value from the previous
   rich-service, if the current function is part of a composite service,
   or the named param. An optional third argument provides a default value for
   the cases where there is no previous response or param with the given name."
  ([req param-name] (get-resp-or-param req param-name nil))
  ([req param-name default-value]
     (or (-> req (map-get :rs-message) (map-get :response))
	 (-> req (map-get :rs-message) (map-get param-name))
	 default-value)))

(defn get-param-as-int
  ([req param-name]
     (get-param-as-int req param-name nil))
  ([req param-name default-val]
     (let [param-val (-> req (map-get :rs-message) (map-get param-name))]
       (if param-val
	 (if (string? param-val)
	   (Integer/parseInt param-val)
	   param-val)
	 default-val))))

(defn compose [& services]
  (apply comp (map get-service-controller (reverse services))))

(defn create-service-controller [service]
  (if (coll? service)
    (apply compose service)
    (get-service-controller service)))

(defn || [& services]
  (let [fns (map #(create-service-controller %) services)]
    (fn [message]
      (apply merge-rs-message-responses
	     (pmap (fn [f] (f message)) fns)))))

(defn if-then
  ([test-services then-services]
     (if-then test-services then-services nil))
  ([test-services then-services else-services]
     (let [test-service (create-service-controller test-services)
	   then-service (create-service-controller then-services)
	   else-service (when else-services (create-service-controller else-services))]
       (fn [message]
	 (let [test-result (test-service message)]
	   (if (get-response test-result)
	     (then-service test-result)
	     (when else-service (else-service test-result))))))))

(defmacro cond-flow
  [& services]
  (when services
    (list 'rich-services.adl/if-then (first services) 
	  (if (next services)
	    (second services)
	    (throw (IllegalArgumentException.
		    "cond-flow requires an even number of forms")))
	  (cons 'rich-services.adl/cond-flow (next (next services))))))

(defn deadline [timeout monitored-services mitigation-services]
  (let [monitored-service (create-service-controller monitored-services)
	mitigation-service (create-service-controller mitigation-services)]
    (fn [message]
      (try (with-timeout timeout (monitored-service message))
	   (catch java.util.concurrent.TimeoutException e
	     (mitigation-service message))))))

(defn scheduler-service [service-fn ms-freq service-args-msg]
  (fn [msg]
    (let [action (get-param msg :action)
	  uri (get-param msg :uri)
	  sc (get-service-controller service-fn)
	  f (fn [] (sc {:uri uri :rs-message service-args-msg}))]
      (condp = action
	  "start" (schedule-periodic f ms-freq ms-freq)
	  "stop" (println "scheduler-service: STOP called")
	  (sc msg)))))

(defn every [frequency service & options]
  (let [options (apply hash-map options)
	freq-map {:second 1000
		  :minute (* 60 1000)
		  :hour (* 60 60 1000)
		  :day (* 24 60 60 1000)}
	ms-freq (freq-map frequency)
	service-args-msg options]
    (scheduler-service service ms-freq service-args-msg)))

(defn service-list
  ([& fns]
     (fn [message]
       (let [service-controllers (map get-service-controller fns)
	     index (get-param-as-int message :index)]
	 (if index
	   (get-response ((nth service-controllers index) message))
	   (map (fn [f] (get-response (f message))) service-controllers))))))

(defn- transform-services [services-map transforms-map]
  (for [k (keys services-map)
	:let [service (get services-map k)
	      transform (get transforms-map k)]]
    [k (cond
	(:pre transform)
	  (compose (:pre transform) service)
	(:post transform)
	  (compose service (:post transform))
	(:pre-post transform)
	  (compose (first (:pre-post transform)) service (second (:pre-post transform)))
	(:bind transform)
	  (:bind transform)
	:else
	  service)]))

(defn rich-service [& services-maps]
  (let [rs-map (apply merge services-maps)
	app-services (:app-services rs-map)
	infra-services (:infra-services rs-map)
	transforms-map (:transforms rs-map)
	schedule-map (:schedule rs-map)
	services (merge app-services infra-services schedule-map)
	transformed-services (transform-services services transforms-map)]
    {:services (reduce conj {} transformed-services)
     :schedule schedule-map}))

(defn app-services
  ([& args] {:app-services (apply hash-map args)}))

(defn infra-services
  ([& args] {:infra-services (apply hash-map args)}))

(defn transforms
  ([& args] {:transforms (apply hash-map args)}))

(defn schedule
  ([& args] {:schedule (apply hash-map args)}))

(defn pre [pre-service]
  {:pre pre-service})

(defn post [post-service]
  {:post post-service})

(defn pre-post [pre-service post-service]
  {:pre-post [pre-service post-service]})

(defn bind [service]
  {:bind service})



