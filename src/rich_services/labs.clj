(ns rich-services.labs
  (:use rich-services.adl
	rich-services.deployment
        [clojure.contrib.str-utils2 :only (grep)])
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

(defn logger 
  "Simple logger constructore; the supplied message, with augmentation, is submitted to the /logger/log service.
   The message is augmented with a timestamp, and is formatted using format. Currently this only works for very
   simple format strings, as in (logger \"example %s\"), which logs the string \"example\" followed by the 
   value of :response on the supplied message rsm. If logger receives a second argument, then this arg is
   evaluated and logged instead of the :response value."
  [msg & args]
    (fn [rsm]
      (let [resp (call-service-by-uri "/logger/log" {"msg" (str (java.util.Date.) ": " (format msg (if args (load-string (first args)) (get-response rsm))))})]
         (get-response rsm))))        

;; Java Swing interop

(def log (ref (JTextArea.))) 

(def logger-re (ref ""))

(defn swing-log-re-append 
  "Basic logging into the text area specified by @log. The text supplied via the 
   rsm value of :msg is grepped against the reg exp pattern obtained via @logger-re. The
   contents of the text area always scroll to the most recent entry."
  [rsm]
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

(defn init-swing-re-logger 
  "Initializes the basic logging facility. Creates the Java Swing components and displays them. Parameters
   for the initialization are extracted from the rsm map."
  [rsm]
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
          (doto (JFrame. (str "Logging with reg exp \"" re "\" on port #" port))
             (.add scrollpane (. BorderLayout CENTER))
             (.setDefaultCloseOperation (. JFrame EXIT_ON_CLOSE))
             (.pack)
             (.setLocation left top)
             (.setVisible true)))))
    rsm))

;; Swing-based logging rich service with rudimentary regexp support

(def swing-re-logger
  (rich-service
    (app-services
      :log   swing-log-re-append
      :start init-swing-re-logger)))
