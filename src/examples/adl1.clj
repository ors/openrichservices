(ns examples.adl1
  (:use rich-services.adl
	rich-services.deployment
	[rich-services.controller :only [get-service-controller]]
	clojure.test
	clojure.pprint
	[clojure.contrib.logging :only (debug)])
  (:require [rich-services.services :as services]
	    [rich-services.controller :as controller]))

(defn conj-resp [output]
  (fn [msg]
    (let [resp (if (coll? (get-response msg))
		 (get-response msg)
		 [(get-response msg)])]
      (concat resp [output]))))

(defn scheduled-service [msg]
  (debug (str (java.util.Date.) ": Scheduled service called."))
  :schedule-service-called)

(def cell-phone (rich-service 
		 
		 (app-services
		  :sensor (conj-resp :sensor-called)
		  :ls (conj-resp :local-store-called)
		  :lc (conj-resp :local-compute-called)
		  :display (conj-resp :display-called)
		  :sense (|| :store (compose :compute :display))
		  :comp (compose :sensor :ls))
		 
		 (infra-services
		  :encrypt (conj-resp :encrypt-called)
		  :decrypt (conj-resp :decrypt-called)
		  :power-high (conj-resp :power-high-called)
		  :power-low (conj-resp :power-low-called)
		  :power-vlow (conj-resp :power-vlow-called)
		  :transfer-data scheduled-service
		  :store (conj-resp :basic-store-called)
		  :compute (conj-resp :basic-compute-called)
		  :rs (conj-resp :remote-store-called)
		  :rc (conj-resp :remote-compute-called))
		 
		 (transforms
		  :rs (pre :encrypt)
		  :rc (pre-post :encrypt :decrypt)
		  :store (bind (cond-flow :power-high (|| :ls :rs)
					  :power-low :ls
					  :power-vlow :skip))
		  :compute (bind (cond-flow :power-high :lc
					    :power-low :rc
					    :power-vlow :rc)))

		 (schedule
		  :transfer (every :minute :transfer-data))))

(def sensor (rich-service (app-services
			   :store (conj-resp :sensor-store-called)
			   :compute (conj-resp :sensor-compute-called)
			   :display (conj-resp :sensor-display-called)
			   :sense (|| :store (compose :cell-phone/compute :display))
			   :comp (compose :cell-phone/encrypt :store))))

(defn deploy-example-nodes []
  (rs-start)
  (println "Sleeping while rich-server master launches...")
  (Thread/sleep 5000)
  (let [[node1 node2 node3] (launch-nodes 3)]
    (println "deploying rich-service instances...")
    (deploy-instance node1 :cell-phone "examples.adl1/cell-phone")
    (deploy-instance node3 :rs3 "examples.rs3/rs3")
    (deploy-instance node2 :sensor "examples.adl1/sensor")
   [node1 node2 node3]))


(defn deploy-example []
  (register-instance :cell-phone cell-phone)
  (register-instance :sensor sensor)
  (rs-start))

(defn stop-example []
  (rs-stop))

(defn print-flow [service]
  (do
    (println "SERVICE:" service)
    (pprint (get-response ((get-service-controller service) {:uri service})))))

(defn get-flow [service]
  (get-response ((get-service-controller service) {:uri service})))

(def service-results {:cell-phone/sense [[[nil "power-high-called" "local-store-called"]
					   [nil "power-high-called" "encrypt-called" "remote-store-called"]]
					  [nil "power-high-called" "local-compute-called" "display-called"]]
		       :sensor/sense [[nil "sensor-store-called"]
				      [nil
				       "power-high-called"
				       "local-compute-called"
				       "sensor-display-called"]]
		       :rs3/helloworld "Hello world"})

(defn test-services []
  (doseq [service (keys service-results)]
    (print-flow service)
    (println "PASS =" (= (service-results service) (get-flow service)))
    (println)))

(defn test-all-local-services []
  (doseq [service (keys @services/*local-app-services*)] (print-flow service)))

  
 