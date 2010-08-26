(ns examples.daemon
  (:use [clojure.contrib.java-utils :only (file)]
        [clojure.contrib.logging :only (log-capture! with-logs)]
        [examples.rs1]))

(defn daemonize
  []
  (.deleteOnExit (file "log/daemon.pid"))
  (.. System out close)
  (.. System err close)
  (deploy-rs1-instance))