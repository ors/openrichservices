(ns examples.adl2
  (:use rich-services.adl
	rich-services.deployment
	[clojure.contrib.logging :only (trace debug info spy)]
        [clojure.contrib.shell-out :only (sh)]
        [clojure.contrib.str-utils2 :only (grep)]
        [rich-services.controller :only [get-service-controller service-controller-proxy
					 lookup-service-fn to-uri]])
  (:require [rich-services.proxy :as proxy])
  (:import [java.awt Dimension BorderLayout]
           [javax.swing JFrame JTextArea JScrollPane SwingUtilities]))
 
(defn conj-resp 
	"Helper function to provide 'default' responses for service calls -- also useful for tracing"
	[output]
  (fn [msg]
    (let [resp (if (coll? (get-response msg))
		 (get-response msg)
		 [(get-response msg)])]
      (concat resp [output]))))

(def cell-power-state (ref 100))

(def cell-phone 
  (rich-service 
    (app-services
      :ls (conj-resp :local-store-called)
      :lc (conj-resp :local-compute-called)
      :display 
        (fn [_] 
            (do (info (str "value arrived @ " (java.util.Date.) " with cell-power-state " @cell-power-state) )
                :cell-display-called))
      :sense 
        (|| :store (compose :compute :display)))

    (infra-services
      :encrypt (conj-resp :encrypt-called)
      :decrypt (conj-resp :decrypt-called)
      :power-high (fn [_] (> @cell-power-state 50))
      :power-low (fn [_] (<= 20 @cell-power-state 50))
      :power-vlow (fn [_] (< @cell-power-state 20))
      :store (conj-resp :basic-store-called)
      :compute (conj-resp :basic-compute-called)
      :rs (conj-resp :remote-store-called)
      :rc (conj-resp :remote-compute-called)
      :skip (conj-resp :skip-called))

    (transforms
      :rs (pre :encrypt)
      :rc (pre-post :encrypt :decrypt)
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
        (every :minute 
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

(def sensor 
  (rich-service 
    (app-services
      :compute (conj-resp :sensor-compute-called)
      :push-value (compose :compute :cell-phone/sense :logger/log))

    (schedule
      :transfer 
      (every :minute :push-value))))

(def log (ref (JTextArea.))) 

(def logger-re (ref ""))

(defn swing-log-re-append [rsm]
  (do
    (SwingUtilities/invokeLater
      (fn []
        (do 
          (dosync 
            (doto @log 
              (.append (apply str (grep (re-pattern @logger-re) [(str (get-resp-or-param rsm :msg) "\n")])))
              (.setCaretPosition (.length (.getText @log)))
              (.invalidate))))))
    rsm))

(defn init-swing-re-logger [rsm]
  (do
    (SwingUtilities/invokeLater
      (fn [] 
        (let [port     (get-param rsm :port)
              left     (get-param-as-int rsm :left)
              top      (get-param-as-int rsm :top)
              re       (get-param rsm :re)
              scrollpane (JScrollPane. @log)]
          (dosync 
            (doto @log
              (.setColumns 80)
              (.setRows 50)
              (.setLineWrap false)
              (.setEditable false))
            (when re
              (ref-set logger-re re)))

          (doto scrollpane
            (.setPreferredSize (Dimension. 560 100)))
          (doto (JFrame. (str "Logging on port #" port))
             (.add scrollpane (. BorderLayout CENTER))
             (.setDefaultCloseOperation (. JFrame EXIT_ON_CLOSE))
             (.pack)
             (.setLocation left top)
             (.setVisible true)))))
    rsm))

(def swing-re-logger
  (rich-service
    (app-services
      :log   swing-log-re-append
      :start init-swing-re-logger)))

(defn call-app-service [node uri & args]
  (apply proxy/get-request (str "http://localhost:" (:port node) uri) args))
 
(defn deploy-demo-nodes []
  (rs-start)
  (println "Sleeping while rich-service master launches...")
  (Thread/sleep 5000)
  (let [[node1 node2 node3] (launch-nodes 3)]
    (println "deploying rich-service instances...")
    (deploy-instance node1 :cell-phone "examples.adl2/cell-phone")
    (deploy-instance node1 :s1 "examples.adl2/sensor")
    (deploy-instance node1 :logger "examples.adl2/swing-re-logger")
    (deploy-instance node2 :cell-phone "examples.adl2/cell-phone")
    (deploy-instance node2 :logger "examples.adl2/swing-re-logger")
    (deploy-instance node2 :s2 "examples.adl2/sensor")
    (deploy-instance node3 :cell-phone "examples.adl2/cell-phone")
    (deploy-instance node3 :logger "examples.adl2/swing-re-logger")
    (deploy-instance node3 :s3 "examples.adl2/sensor")
    (Thread/sleep 4000)
    (call-app-service node1 "/logger/start" {:port (:port node1) :left 100 :top 100 :re ""})
    (call-app-service node2 "/logger/start" {:port (:port node2) :left 700 :top 100 :re "displayed"})
    (call-app-service node3 "/logger/start" {:port (:port node3) :left 1300 :top 100 :re "received"})
    [node1 node2 node3]))
