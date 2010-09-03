(ns rich-services.labs.loggers.core
  (:use rich-services.adl
        [rich-services.labs.interop :only [call-service-by-uri]]))
 
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
