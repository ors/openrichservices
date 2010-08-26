(defproject rich-services "1.0.0-SNAPSHOT"
  :description "An implementation of Open RichServices"
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
		 [ring/ring-core "0.2.5"]
		 [ring/ring-devel "0.2.5"]
		 [ring/ring-jetty-adapter "0.2.5"]
		 [clojure-http-client "1.0.1"]
		 [log4j "1.2.16"]]
  :dev-dependencies [[swank-clojure "1.3.0-SNAPSHOT"]]
  :main rich-services.deployment)
