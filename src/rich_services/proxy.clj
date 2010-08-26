(ns ^{:doc "Prototype Rich-Services Infrastructure: remote service proxy functions."
      :author "David Edgar Liebke"}
  rich-services.proxy
  (:use [clojure.contrib.json :only (read-json json-str)]
	[rich-services.util :only [map-get]])
  (:require [clojure-http.client :as client]
	    [clojure-http.resourcefully :as res]))

(defn get-request
  ([url] (get-request url {}))
  ([url params]
     {:pre [(or (nil? params) (map? params))]}
     (try
       (let [resp (res/get url {} params)
	    result (read-json (apply str (:body-seq resp)))]
	 result)
       (catch java.io.IOException e
	     {:status 404
	      :body (json-str {:status 404
			       :message (str "Service not found: " url)})}))))


