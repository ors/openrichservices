(ns 
  ^{:doc "Authentication and authorization functions."
    :author "David Edgar Liebke"}
  rich-services.security
  (:import java.security.MessageDigest
	   sun.misc.BASE64Encoder))

(defn get-sha1-string [value]
  (let [md (MessageDigest/getInstance "SHA-1")
	base64-encoder (BASE64Encoder.)]
    (.update md (.getBytes value))
    (.encode base64-encoder (.digest md))))