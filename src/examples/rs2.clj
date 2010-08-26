(ns ^{:doc "A second Rich-Service instance"}
  examples.rs2
  (:use [rich-services.adl]
	[examples.services])
  (:require [rich-services.deployment :as deployment]))

(defn deploy-rs2-instance []
  (deployment/rs-start {:port 9090 :master-registrar "http://localhost:8080"})
  (register-instance :rs2
		     (rich-service
		      (app-services
		       :helloworld hello-world-service
		       :to-upper to-upper-service
		       :interpose interpose-service
		       :comp1 (compose :helloworld :to-upper)
		       :comp2 (compose :helloworld :to-upper :interpose)
		       :comp3 (compose :to-upper :interpose :helloworld)))))

(defn shutdown-rs2-instance []
  (deployment/rs-stop))



