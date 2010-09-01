(ns examples.adl2
  (:use rich-services.adl
	rich-services.deployment
	[clojure.contrib.logging :only (trace debug info spy)]
        [clojure.contrib.shell-out :only (sh)]
        [clojure.contrib.str-utils2 :only (grep)]
        [rich-services.controller :only [get-service-controller service-controller-proxy
					 lookup-service-fn to-uri]]
        [rich-services.util :only [get-rs-param]])
  (:require [rich-services.proxy :as proxy]
            [rich-services.config :as config])
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

(defn call-service-by-uri [uri & args]
   (apply proxy/get-request (str "http://" (config/get-hostname) ":" (config/get-app-port) uri) args))

(defn call-service-on-node [node uri & args]
   (apply proxy/get-request (str "http://" (config/get-hostname) ":" (:port node) uri) args))

(defn logger [msg]
  (fn [rsm]
    (let [resp (call-service-by-uri "/logger/log" {"msg" (str (java.util.Date.) ": " msg)})]
      (get-rs-param resp :response))))         

(def cell-phone 
  (rich-service 
    (app-services
      :ls (logger "local store called")
      :lc (logger "local compute called")
      :display (logger (str "value arrived with cell-power-state " @cell-power-state))
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

(def sensor 
  (rich-service 
    (app-services
      :compute (fn [-] (rand-int 100))
      :push-value (compose :compute :cell-phone/sense))

    (schedule
      :transfer 
      (every :second :push-value))))

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
    (call-service-on-node node1 "/logger/start" {:port (:port node1) :left 100 :top 100 :re "enc"})
    (call-service-on-node node2 "/logger/start" {:port (:port node2) :left 700 :top 100 :re ".*"})
    (call-service-on-node node3 "/logger/start" {:port (:port node3) :left 1300 :top 100 :re "value"})
    [node1 node2 node3]))
