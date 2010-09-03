(ns rich-services.labs.interop
    (:use rich-services.adl
          rich-services.deployment)
    (:require [rich-services.proxy :as proxy]
              [rich-services.config :as config]))
 
(defn conj-resp 
    "Helper function to provide 'default' responses for service calls -- also useful for tracing"
    [output]
    (fn [msg]
          (let [resp (if (coll? (get-response msg))
                           (get-response msg)
                           [(get-response msg)])]
                  (concat resp [output]))))

(defn call-service-by-uri 
    "Finds the service specified by the uri, then calls it with the supplied args. As per
       proxy/get-request, the lookup only defers to the master registrar if the service is not 
       available on the current node."
    [uri & args]
      (apply proxy/get-request (str "http://" (config/get-hostname) ":" (config/get-app-port) uri) args))

(defn call-service-on-node 
    "Finds the service specified by the uri on the specified node, then calls it with the supplied args. As per
       proxy/get-request, the lookup only defers to the master registrar if the service is not 
       available on the specified node."
    [node uri & args]
      (apply proxy/get-request (str "http://" (config/get-hostname) ":" (:port node) uri) args))
