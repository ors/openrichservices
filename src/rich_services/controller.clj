(ns ^{:doc "Prototype Rich-Services Infrastructure: service-controller functions."
      :author "David Edgar Liebke"}
  rich-services.controller
  (:use [rich-services.services :only [instance-and-service *global-app-services* *local-app-services*]]
	[rich-services.util :only [map-get get-rs-param]]
	[clojure.contrib.json :only (json-str)]
	[clojure.contrib.logging :only (debug)])
  (:require [rich-services.config :as config]
	    [rich-services.proxy :as proxy]))

(defn- compact [m]
  (into {} (filter second m)))


(def global-app-lookup
     ;; memoize?
     (fn [uri]
       (or (get @*global-app-services* uri)
	   (let [resp (when (config/slave-mode?)
			(proxy/get-request (str (config/get-master-registrar) "/registrar/lookup")
					   {"service-uri" uri}))]
	     (get-rs-param resp :response)))))

(defn not-found [req]
  {:status 404
   :body (json-str {:status 404
		    :message (str "Service not found: "
				  (:uri req))})})

(defn rs-message? [message]
  (and (map? message) (map? (:rs-message message))))

(defn req-pre-processor [instance-name req]
  (let [uri (map-get req :uri)
	rs-message (merge (map-get req :params)
			  {:uri uri}
			  (map-get req :rs-message)
			  {:instance-name instance-name})]
    (assoc req :rs-message rs-message)))

(defn resp-post-processor [instance-name pre-processed-req response]
  (let [rs-mesg (or (:rs-message pre-processed-req) {})
	resp (if (rs-message? response)
	       (get-rs-param response :response)
	       response)
	new-rs-mesg (compact (-> rs-mesg
				 (dissoc "response")
				 (assoc :response resp)
				 (assoc :instance-name instance-name)))
	body (json-str {:rs-message new-rs-mesg})]
    (debug (str "resp-post-processor: body = " (pr-str new-rs-mesg)))
    {:status 200
     :body body
     :rs-message new-rs-mesg}))

(defn infer-instance-name [service-name req]
  (let [uri (map-get req :uri)
	[absolute-instance-name _] (instance-and-service service-name)
	rsm-instance-name (-> req :rs-message :instance-name)
	[uri-instance-name _] (instance-and-service uri)
	current-instance-name (or absolute-instance-name rsm-instance-name uri-instance-name)
	previous-instance-name (or rsm-instance-name current-instance-name)]
    [current-instance-name previous-instance-name]))

(declare lookup-service-fn)

(defn service-controller [service]
  ^{:service-controller true}
  (fn [req]
    (let [[current-instance-name previous-instance-name] (infer-instance-name service req)
	  pre-processed-req (req-pre-processor current-instance-name req)
	  service-function (if (fn? service)
			     service
			     (lookup-service-fn current-instance-name service))
	  service-resp (service-function pre-processed-req)]
      (debug (str "service-controller: pre-processed-req = " (pr-str (:rs-message pre-processed-req))))
      (debug (str "service-controller: service-resp = " (pr-str service-resp)))
      (resp-post-processor previous-instance-name pre-processed-req service-resp))))

(defn service-controller-proxy [base-url uri]
  (service-controller
    (fn [req]
      (debug (str "rs-app-proxy: url " base-url uri))
      (let [resp (proxy/get-request (str base-url uri)
				    (map-get req :rs-message))]
	(debug (str "rs-app-proxy-fn: resp: " resp))
	resp))))

(defn maybe-namespace [x]
  (when (keyword? x)
    (namespace x)))

(defn to-uri
  ([instance-name service-name]
     (str "/" (or (maybe-namespace service-name) (name (or instance-name instance-name)))
	  "/" (name service-name))))

(defn lookup-service-fn
  ([instance-name uri]
     ;; (println "lookup-service-fn uri: " (to-uri instance-name uri))
     (lookup-service-fn (to-uri instance-name uri)))
  ([uri]
     (let [local-app-fn (get @*local-app-services* uri)
	   _ (debug (str "lookup-service-fn uri: " uri ", local-app-fn: " (str local-app-fn)))
	   app-fn (if local-app-fn
		    local-app-fn
		    (let [global-app-url (global-app-lookup uri)]
		      (debug (str "lookup-service-fn uri: " uri ", global-app-url: " global-app-url))
		      (when global-app-url
			(service-controller-proxy global-app-url uri))))]
       (or app-fn not-found))))

(defn service-controller? [f] (:service-controller (meta f)))

(def get-service-controller
     ;;     memoize
     (fn [service]
       (if (service-controller? service)
	 service
	 (service-controller service))))

