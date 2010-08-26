(ns ^{:doc "A simple Rich-Service instance with a few basic and composed app-services."}
  examples.rs3
  (:use rich-services.adl
	examples.services)
  (:require [rich-services.deployment :as deployment]))

(def rs3 (rich-service
	  (app-services
	   :helloworld hello-world-service
	   :echo echo-service
	   :to-upper to-upper-service
	   :to-lower to-lower-service
	   :interpose interpose-service
	   :reduce reduce-service
	   :longrun long-running-service
	   :false (fn [msg] false)
	   :true (fn [msg] true)
	   :rofl (fn [msg] "RoFl")
	   :mitigation (fn [msg] "Deadline for monitored service has elapsed.")
	   :apps (apply service-list (for [i (range 10)]
				       (fn [message]
					 (str "Result from function " i ": "
					      (get-response message)))))
	   :comp1 (compose :helloworld :to-upper)
	   :comp2 (compose :helloworld :to-upper :interpose)
	   :comp3 (compose :to-upper :interpose :helloworld)
	   :comp4 (compose :helloworld :to-upper :interpose :to-lower)
	   :remote-comp1 (compose :helloworld :to-upper)
	   :remote-comp2 (compose :helloworld :to-upper :rs2-interpose)
	   :remote-comp3 (compose :helloworld :rs2-to-upper)
	   :parallel (|| :helloworld :log :to-lower)
	   :mapreduce (compose (|| :helloworld :log :to-lower) :reduce)
	   :notparallel (compose :helloworld :log :to-lower)
	   :test-if-then-else (if-then :echo :helloworld :to-upper)
	   :test-if-then (if-then :echo :helloworld)
	   :test-if-then-true (compose :helloworld (if-then :echo :to-upper) :interpose)
	   :test-if-then-false (compose :helloworld (if-then :false :to-upper) :interpose)
	   :test-if-then-true-comb (compose :helloworld (if-then [:rofl :echo] [:to-upper :log] [:to-lower :log])
					    :interpose)
	   :test-if-then-false-comb (compose :helloworld (if-then [:echo :false] [:to-upper :log] [:rofl :log])
					     :interpose)
	   :deadline (deadline 100 :longrun :mitigation)
	   :map-apps (compose :echo :apps)
	   :app-select (compose :echo :apps))

	  (infra-services
	   :log logging-service)

	  (transforms
	   :apps (post :reduce))))

(defn deploy-rs3-instance []
  (deployment/rs-start {:port 8888})
  (deployment/register-instance :rs3 rs3))

(defn shutdown-rs3-instance []
  (deployment/rs-stop))