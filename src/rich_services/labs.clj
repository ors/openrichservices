(ns rich-services.labs
  (:use rich-services.adl
	rich-services.deployment
        [clojure.contrib.str-utils2 :only (grep)]
        [clojure.contrib.logging :only [spy debug]])
  (:require [rich-services.proxy :as proxy]
            [rich-services.config :as config])
  (:import [java.awt Dimension BorderLayout]
           [java.awt.event ActionListener]
           [javax.swing BoxLayout JFrame JPanel JButton JTextArea JScrollPane SwingUtilities]))
 
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
  "Simple logger constructor; the supplied message, with augmentation, is submitted to the /logger/log service.
   The message is augmented with a timestamp, and is formatted using format. Currently this only works for very
   simple format strings, as in (logger \"example %s\"), which logs the string \"example\" followed by the 
   value of :response on the supplied message rsm. If logger receives a second argument, then this arg is
   evaluated and logged instead of the :response value."
  [msg & args]
    (fn [rsm]
      (let [resp (call-service-by-uri "/logger/log" {"msg" (str (java.util.Date.) ": " (format msg (if args (load-string (first args)) (get-response rsm))))})]
         (get-response rsm))))        

;; Java Swing interop

(def text-area-log (JTextArea.)) 

(def logger-re "")

(def logger-state (ref :running))

(def logger-queue (agent []))

(defn- update-textarea [msgs]
  "Writes the messages of vector msgs into the JTextArea given by text-area-log.
   If the maximum number of rows is reached, the JTextArea is cleard; then loggin
   continues."
  (do 
    (when-not (empty? msgs)
      (SwingUtilities/invokeLater
        (fn []
	  (doseq [m msgs]
            (if (= (. text-area-log getLineCount)
                   (. text-area-log getRows))
              (. text-area-log setText ""))
            (doto text-area-log 
              (.append m)
              (.setCaretPosition (.length (.getText text-area-log)))
              (.invalidate))))))
    []))

(defn- update-log [msg]
  (do (send logger-queue conj msg)
      (send logger-queue update-textarea)))

(defn- swing-log-re-append 
  "Basic logging into the text area specified by text-area-log. The text supplied via the 
   rsm value of :msg is grepped against the reg exp pattern obtained via logger-re. The
   contents of the text area always scroll to the most recent entry."
  [rsm]
  (dosync  
    (let [msg (apply str (grep logger-re [(str (get-resp-or-param rsm :msg) "\n")]))]
      (when-not (= @logger-state :stopped)
        (cond
          (= @logger-state :paused)
            (send logger-queue conj msg)
          (= @logger-state :continued)
            (do (send logger-queue conj msg)
                (send logger-queue update-textarea)
                (ref-set logger-state :running))
          (= @logger-state :running)
            (do (send logger-queue conj msg)
                (send logger-queue update-textarea)))))
    rsm))

(defn- init-swing-re-logger 
  "Initializes the basic logging facility. Creates the Java Swing components and displays them. Parameters
   for the initialization are extracted from the rsm map."
  [rsm]
  (do
    (SwingUtilities/invokeLater
      (fn [] 
        (let [port        (get-param rsm :port)
              left        (get-param-as-int rsm :left)
              top         (get-param-as-int rsm :top)
              re          (get-param rsm :re)
              buttonpanel (JPanel.) 
              stopbutton  (JButton. "STOP")
              pausebutton (JButton. "PAUSE")
              playbutton  (JButton. "PLAY")
              scrollpane  (JScrollPane. text-area-log)
              mainpanel   (JPanel.)
              r-prefix    "[RUNNING] "
              p-prefix    "[PAUSED]  "
              s-prefix    "[STOPPED] "
              t-prefix    (ref r-prefix)
              t-suffix    (str "Logging with reg exp \"" re "\" on port #" port)
              title       (agent (str @t-prefix t-suffix))
              frame       (JFrame. @title)
              change-title (fn [title] (let [new-title (str @t-prefix t-suffix)] (. frame setTitle new-title) new-title))]
          (dosync 
            (doto text-area-log
              (.setColumns 80)
              (.setRows 1000)
              (.setLineWrap true)
              (.setEditable false))
            (when re
              (def logger-re (re-pattern re))))
          (doto playbutton
            (.addActionListener 
              (proxy [ActionListener] []
                (actionPerformed [event] 
                  (dosync 
                    (when (or (= @logger-state :paused)
                              (= @logger-state :stopped))
                      (ref-set logger-state :continued)
                      (ref-set t-prefix r-prefix)
                      (send title change-title)))))))
          (doto pausebutton
            (.addActionListener 
              (proxy [ActionListener] []
                (actionPerformed [event] 
                  (do
                    (dosync 
                      (when (= @logger-state :running)
                        (ref-set logger-state :paused)
                        (ref-set t-prefix p-prefix)
                        (send title change-title))))))))
          (doto stopbutton
            (.addActionListener 
              (proxy [ActionListener] []
                (actionPerformed [event] 
                  (dosync 
                    (ref-set logger-state :stopped)
                    (ref-set t-prefix s-prefix)
                    (send title change-title))))))
          (doto buttonpanel
            (.setLayout (BoxLayout. buttonpanel (. BoxLayout X_AXIS)))
            (.add pausebutton)
            (.add stopbutton)
            (.add playbutton))
          (doto scrollpane
            (.setPreferredSize (Dimension. 560 100)))
          (doto mainpanel
            (.setLayout (BoxLayout. mainpanel (. BoxLayout Y_AXIS)))
            (.add buttonpanel)
            (.add scrollpane (. BorderLayout CENTER)))
          (doto frame
             (.add mainpanel)
             (.setDefaultCloseOperation (. JFrame EXIT_ON_CLOSE))
             (.pack)
             (.setLocation left top)
             (.setVisible true))
          (dosync 
            (ref-set logger-state :running)))))
    rsm))

;; Swing-based logging rich service with rudimentary regexp support

(def swing-re-logger
  (rich-service
    (app-services
      :log   swing-log-re-append
      :start init-swing-re-logger)))
