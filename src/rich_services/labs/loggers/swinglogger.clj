(ns rich-services.labs.loggers.swinglogger
  (:use rich-services.adl
        [clojure.contrib.str-utils2 :only (grep)])
  (:import [java.awt Dimension BorderLayout]
           [java.awt.event ActionListener]
           [javax.swing BoxLayout JFrame JPanel JButton JTextArea JScrollPane SwingUtilities]))
 
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
            (do (update-log msg)
	        (ref-set logger-state :running))
          (= @logger-state :running)
            (do (update-log msg)))))
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
