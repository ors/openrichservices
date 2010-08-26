(ns rich-services.config)

(defn get-app-port []
  (Integer/parseInt (System/getProperty "rs.port" "8080")))

(defn get-hostname []
  (System/getProperty "hostname" "localhost"))

(defn get-master-registrar []
  (System/getProperty "master.registrar" (str "http://" (get-hostname) ":" (get-app-port))))

(defn slave-mode? []
  (= "true" (System/getProperty "slave.mode" "false")))
