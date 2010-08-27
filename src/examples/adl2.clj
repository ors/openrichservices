(ns examples.adl2
  (:use rich-services.adl
	rich-services.deployment
	[clojure.contrib.logging :only (debug info)]))

(defn conj-resp 
	"Helper function to provide 'default' responses for service calls -- also useful for tracing"
	[output]
  (fn [msg]
    (let [resp (if (coll? (get-response msg))
		 (get-response msg)
		 [(get-response msg)])]
      (concat resp [output]))))

(def cell-power-state (ref 100))

(def cell-phone (rich-service 
		 
		 (app-services
		  :ls (conj-resp :local-store-called)
		  :lc (conj-resp :local-compute-called)
		  :display (fn [rsm] 
			        (do (info (str "value arrived @ " (java.util.Date.) " with cell-power-state " @cell-power-state) )
			            :cell-display-called))
		  :sense (|| :store (compose :compute :display)))
		 
		 (infra-services
		  :encrypt (conj-resp :encrypt-called)
		  :decrypt (conj-resp :decrypt-called)
		  :power-high (fn [_] (> @cell-power-state 50))
		  :power-low (fn [_] (some #(= % @cell-power-state) (range 20 49)))
		  :power-vlow (fn [_] (< @cell-power-state 20))
		  :store (conj-resp :basic-store-called)
		  :compute (conj-resp :basic-compute-called)
		  :rs (conj-resp :remote-store-called)
		  :rc (conj-resp :remote-compute-called)
		  :skip (conj-resp :skip-called))
		 
		 (transforms
		  :rs (pre :encrypt)
		  :rc (pre-post :encrypt :decrypt)
		  :store (bind (cond-flow :power-high (|| :ls :rs)
					  :power-low :ls
					  :power-vlow :skip))
		  :compute (bind (cond-flow :power-high :lc
					    :power-low :rc
					    :power-vlow :skip)))
		(schedule
		  :power-drain (every :minute 
			             (fn [_]
			               (dosync (if (>= @cell-power-state 20)
			                         (do (ref-set cell-power-state (- @cell-power-state 20))
			                             :power-drained-by-20)
			                         :power-too-low)))))))

(def sensor (rich-service (app-services
			   :compute (conj-resp :sensor-compute-called)
			   :push-value (compose :compute :cell-phone/sense))
			(schedule
				:transfer (every :minute :push-value))))

(defn deploy-example-nodes []
  (rs-start)
  (println "Sleeping while rich-service master launches...")
  (Thread/sleep 5000)
  (let [[node1 node2 node3] (launch-nodes 3)]
    (println "deploying rich-service instances...")
    (deploy-instance node1 :cell-phone "examples.adl2/cell-phone")
    (deploy-instance node1 :sensor "examples.adl2/sensor")
   [node1 node2 node3]))