(ns ^{:doc "Prototype Rich-Services Infrastructure: web app functions"
      :author "David Edgar Liebke"}
  rich-services.deployment
  (:gen-class)
  (:use [rich-services.adl :only [get-param-as-int get-param rich-service app-services]]
	[rich-services.services :only [register-local-app-service register-app-service
				       base-url]]
	[rich-services.controller :only [get-service-controller service-controller-proxy
					 lookup-service-fn to-uri]]
	[ring.adapter.jetty :only [run-jetty]]
	[ring.middleware.params :only [wrap-params]]
	[ring.middleware.stacktrace :only [wrap-stacktrace]]
	[clojure.stacktrace :only [print-cause-trace]]
	[clojure.contrib.logging :only [debug info warn error spy]])
  (:require [rich-services.services.registrar :as registrar]
	    [rich-services.config :as config]
	    [rich-services.proxy :as proxy]))

(def *server* (ref nil))

(def *slave-nodes* (ref #{}))

(def *pending-slave-nodes* (ref #{}))

(def *pending-slave-deployments* (ref {}))

(declare handle-pending-slave-deployments)

(defn register-slave [host port]
  (let [node-map {:host host, :port port}
	pending-deployments  (dosync
			      (alter *pending-slave-nodes* disj node-map)
			      (alter *slave-nodes* conj node-map)
			      (let [pending-deployments (@*pending-slave-deployments* node-map)]
				(alter *pending-slave-deployments* dissoc node-map)
				pending-deployments))]
    (handle-pending-slave-deployments node-map pending-deployments)))

(defn pending-slave? [node-map]
  (@*pending-slave-nodes* node-map))

(defn known-slave? [node-map]
  (@*slave-nodes* node-map))

(defn add-pending-slave [host port]
  (dosync
   (alter *pending-slave-nodes* conj {:host host, :port port})))

(defn rs-router [req]
  (debug (str "(rs-router " (pr-str req) ")"))
  (spy ((lookup-service-fn (:uri req)) req)))

(defn register-service
  "Creates an instance of an application-service from the given function, accessible
   from the given URI."
  ([service-ns service-name f]
     (let [uri (to-uri service-ns service-name)]
       (register-app-service uri (base-url))
       (register-local-app-service uri (get-service-controller f))
       uri)))

(defn register-instance [instance-name rich-service-map]
  (let [services-map (:services rich-service-map)
	schedule-map (:schedule rich-service-map)]
    (doseq [k (keys services-map)]
      (register-service instance-name k (get services-map k)))
    (doseq [k (keys schedule-map)]
      (let [scheduled-service (get-service-controller (get schedule-map k))
	    uri (to-uri instance-name k)]
	(scheduled-service {:uri uri :rs-message {:action "start"}})))
    rich-service-map))

(defn find-open-port [used-ports]
  (loop []
    (let [x (+ 10000 (rand-int 30000))]
      (if (used-ports x)
	(recur)
	(if-let [server (try (java.net.ServerSocket. x)
			     (catch java.io.IOException e false))]
	  (do (.close server) x)
	  (recur))))))

(defn server-command-line [port]
  (let [master (config/get-master-registrar)
	cmd (str "/usr/bin/java -Drs.port=" port " -Dhostname=" (config/get-hostname)
		 " -Dmaster.registrar=" master
		 " -Dslave.mode=true"
		 " -jar rich-services-1.0.0-SNAPSHOT-standalone.jar")]
    (debug (str "server-command-line: " cmd))
    cmd))

(defn launch-jvm-server [used-ports]
  (let [port (find-open-port used-ports)]
    (add-pending-slave (config/get-hostname) port)
    (.. Runtime getRuntime 
	(exec (server-command-line port)))
    port))

(defn launch-jvm-servers
  "Launches n new JVMs running the rich services infrastructure on
  random ports.  Returns a set of ports on which the new servers are
  listening."
  [n]
  (reduce (fn [ports _] (conj ports (launch-jvm-server ports)))
	  #{} (range n)))

(defn load-var
  "s is the namespace-qualified name of a Var, as a String.  Loads the
  namespace, if necessary, and returns the root binding of the Var."
  [s] {:pre [(string? s)
	     (re-matches #"[^/]+/[^/]+" s)]}
  (let [sym (symbol s)
	n (symbol (namespace sym))]
    (spy (require n))
    (var-get (resolve sym))))

(defn register-instance-str
  [instance-name var-name]
  {:pre [(keyword? instance-name)
	 (string? var-name)]}
  (register-instance instance-name (load-var var-name)))

(defn launch-nodes [count]
  (map (fn [port] {:host (config/get-hostname) :port port})
       (launch-jvm-servers count)))

(defn deploy-instance-to-known-slave
  [node-map instance-name rich-service-name]
  (let [{:keys [host port]} node-map
	base-url (str "http://" host ":" port)
	uri "/deploy/instance"
	sc-proxy (service-controller-proxy base-url uri)]
    (sc-proxy {:rs-message {:name instance-name
			    :rich-service rich-service-name}})))

(defn handle-pending-slave-deployments [node-map deployments]
  (doseq [deployment deployments]
    (apply deploy-instance-to-known-slave node-map deployment)))

(defn add-pending-slave-deployment
  [node-map instance-name rich-service-name]
  (dosync
   (alter *pending-slave-deployments*
	  update-in [node-map] conj
	  [instance-name rich-service-name])))

(defn deploy-instance
  "Deploys an instance of a rich service on another server.  node-map
  is a map of :host and :port identifying the remote server, which must
  be running the rich-services infrastructure.  instance-name is a
  keyword, which is the identifying name of the new instance.
  rich-service-name is the fully-qualified name of a Clojure Var, given
  as a string.  The remote server will attempt to load the rich service
  definition from that Var.

  If the given remote server has not yet registered itself with the
  master node, deploy-instance will throw an Exception."
  ([node-map instance-name rich-service-name]
     (cond (known-slave? node-map)
	     (deploy-instance-to-known-slave node-map instance-name rich-service-name)
	   (pending-slave? node-map)
	     (add-pending-slave-deployment node-map instance-name rich-service-name)
	   :else
	     (throw (Exception. (str "No slave node registered or pending at " (pr-str node-map)))))))

(defn launch-node-service [message]
  (let [new-ports (launch-jvm-servers (get-param-as-int message :count))]
    {:ports new-ports}))

(defn deploy-instance-service [message]
  (let [instance-name (keyword (get-param message :name))
	rich-service-name (get-param message :rich-service)
	server-url (base-url)]
    (register-instance-str instance-name rich-service-name)
    {:name instance-name
     :rich-service rich-service-name
     :server-url server-url}))

(defn deploy-deployment-service []
  (register-instance :deploy
		     (rich-service
		      (app-services
		       :node launch-node-service
		       :instance deploy-instance-service))))

(defn register-slave-service [req]
  (let [slave-host (get-param req "host")
	slave-port (get-param-as-int req "port")]
    (register-slave slave-host slave-port)))

(defn deploy-registrar-service []
  (when-not (config/slave-mode?)
   (register-instance :registrar
		      (rich-service
		       (app-services
			:register registrar/register-service
			:lookup registrar/lookup-service
			:slave register-slave-service)))))

(def server-defaults
     {:port (config/get-app-port)
      :hostname (config/get-hostname)
      :master-registrar (config/get-master-registrar)})

(defn register-with-master []
  (proxy/get-request (str (config/get-master-registrar) "/registrar/slave")
		     {:host (config/get-hostname)
		      :port (config/get-app-port)}))

(defn rs-start
  ([] (rs-start nil))
  ([options]
     (let [{:keys [hostname port master-registrar]} (merge server-defaults options)
	   root (-> rs-router (wrap-params) (wrap-stacktrace))]
       (info "Launching Rich Services Infrastructure...")
       (info (str "Master Registrar: " master-registrar))
       (base-url (str "http://" hostname ":" port))
       (dosync
	(ref-set *server*
		 (run-jetty root
			    {:port (or port 8080)
			     :join? false})))
       (info "Rich Services Infrastructure launched")
       (info "Launching Registrar Services...")
       (deploy-registrar-service)
       (deploy-deployment-service)
       (info "Registrar Services launched.")
       (when (config/slave-mode?)
	 (register-with-master)))))

(defn rs-stop []
  (info "Shutting down Rich Services Infrastructure...")
  (.stop @*server*)
  (info "Stopped."))

(defn -main [& args]
  (rs-start))
