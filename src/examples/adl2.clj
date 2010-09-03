(ns examples.adl2
  (:use rich-services.adl
	rich-services.deployment
        [rich-services.labs :only [conj-resp call-service-by-uri call-service-on-node logger swing-re-logger]]))
 
(def cell-power-state (ref 100))

(def cell-phone 
  (rich-service 
    (app-services
      :ls (logger "local store called")
      :lc (logger "local compute called")
      :display (logger "display value %s")
      :sense 
        (|| :store (compose :compute :display)))

    (infra-services
      :encrypt (logger "encrypt called")
      :decrypt (logger "decrypt called")
      :power-high (fn [_] (> @cell-power-state 50))
      :power-low (fn [_] (<= 20 @cell-power-state 50))
      :power-vlow (fn [_] (< @cell-power-state 20))
      :store (logger "basic store called")
      :compute (logger "basic compute called")
      :rs (logger "remote store called")
      :rc (logger "remote compute called")
      :skip (logger "skip called"))

    (transforms
      :rs (pre (compose (logger "performing encryption before remote storage") :encrypt))
      :rc (pre-post (compose (logger "performing encryption before remote computation") :encrypt)
                    (compose (logger "performing decryption after receiving remote result") :decrypt))
      :store 
        (bind 
          (cond-flow :power-high (|| :ls :rs)
                     :power-low :ls
                     :power-vlow :skip))
      :compute 
        (bind 
          (cond-flow :power-high :lc
                     :power-low :rc
                     :power-vlow :skip)))
    
    (schedule
      :power-drain 
        (every :second 
          (fn [_]
              (dosync 
                (if (>= @cell-power-state 20)
                    (do (ref-set cell-power-state (- @cell-power-state (rand-int 21)))
                        :power-drained)
                    :power-too-low))))
      :daily-transfer 
        (every :day 
          (if (not :power-low)
              :rs
              :skip)))))

(def svalue (ref 0))

(def sensor 
  (rich-service 
    (app-services
      :compute (fn [_] (dosync (ref-set svalue (inc @svalue))))
      :push-value (compose :compute :cell-phone/sense))

    (schedule
      :transfer 
      (every :second :push-value))))

(defn deploy-demo-nodes []
  (rs-start)
  (println "Sleeping while rich-service master launches...")
  (Thread/sleep 5000)
  (let [[node1 node2 node3] (launch-nodes 3)]
    (println "deploying rich-service instances...")
    (deploy-instance node1 :cell-phone "examples.adl2/cell-phone")
    (deploy-instance node1 :s1 "examples.adl2/sensor")
    (deploy-instance node1 :logger "rich-services.labs/swing-re-logger")
    (deploy-instance node2 :cell-phone "examples.adl2/cell-phone")
    (deploy-instance node2 :logger "rich-services.labs/swing-re-logger")
    (deploy-instance node2 :s2 "examples.adl2/sensor")
    (deploy-instance node3 :cell-phone "examples.adl2/cell-phone")
    (deploy-instance node3 :logger "rich-services.labs/swing-re-logger")
    (deploy-instance node3 :s3 "examples.adl2/sensor")
    (println "Sleeping while instances deploy...")
    (Thread/sleep 4000)
    (println "Starting Swing loggers...")
    (call-service-on-node node1 "/logger/start" {:port (:port node1) :left 100 :top 100 :re "enc|dec"})
    (call-service-on-node node2 "/logger/start" {:port (:port node2) :left 700 :top 100 :re ".*"})
    (call-service-on-node node3 "/logger/start" {:port (:port node3) :left 1300 :top 100 :re "value"})
    [node1 node2 node3]))
