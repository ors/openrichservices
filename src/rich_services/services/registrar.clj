(ns ^{:doc "Rich-Services registration services."
      :author "David Edgar Liebke"}
  rich-services.services.registrar
  (:use  [rich-services.adl :only [get-param get-param-as-int]]
	 [rich-services.services :only [register-app-service]]
	 [rich-services.controller :only [global-app-lookup]]))

(defn register-service [req]
  (let [service-uri (get-param req "service-uri")
	service-location (get-param req "service-location")]
    (when (and service-uri service-location)
      (if (register-app-service service-uri service-location)
       "Registration successful."
       "Registration unsuccessful."))))

(defn lookup-service [req]
  (let [service-uri (get-param req "service-uri")]
    (when service-uri (global-app-lookup service-uri))))

