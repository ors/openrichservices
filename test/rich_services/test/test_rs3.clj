(ns rich-services.test.test-rs3
  (:use [clojure.test])
  (:require [rich-services.proxy :as proxy]
	    [examples.rs3 :as rs3]))


(defn server-fixture [t]
  (rs3/deploy-rs3-instance)
  (t)
  (rs3/shutdown-rs3-instance))

(use-fixtures :once server-fixture)

(deftest simple-services-test
  (is (= (proxy/get-request "http://localhost:8888/rs3/helloworld" {})
	 {:rs-message {:response "Hello world"
		       :uri "/rs3/helloworld"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/helloworld" {"name" "David"})
	 {:rs-message {:response "Hello David"
		       :uri "/rs3/helloworld"
		       :name "David"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/to-upper" {})
	 {:rs-message {:response "TO-UPPER"
		       :uri "/rs3/to-upper"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/to-upper" {"value" "foobar"})
	 {:rs-message {:response "FOOBAR"
		       :uri "/rs3/to-upper"
		       :value "foobar"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/interpose" {})
	 {:rs-message {:response "i-n-t-e-r-p-o-s-e"
		       :uri "/rs3/interpose"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/interpose" {"value" "barbaz"})
	 {:rs-message {:response "b-a-r-b-a-z"
		       :uri "/rs3/interpose"
		       :value "barbaz"
		       :instance-name "rs3"}})))

(deftest error-cases
  (is (= (proxy/get-request "http://localhost:8888/rs3/notfound")
	 {:status 404
	  :body "{\"status\":404,\"message\":\"Service not found: http:\\/\\/localhost:8888\\/rs3\\/notfound\"}"}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/to-upper" {"valuuuuu" "foobar"})
	 {:rs-message {:response "TO-UPPER"
		       :uri "/rs3/to-upper"
		       :valuuuuu "foobar"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/to-upper" {"value" nil})
	 {:rs-message {:response ""
		       :uri "/rs3/to-upper"
		       :value ""
		       :instance-name "rs3"}})))

(deftest composed-services-test
  (is (= (proxy/get-request "http://localhost:8888/rs3/comp1")
	 {:rs-message {:response "HELLO WORLD"
		       :uri "/rs3/comp1"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/comp1" {:name "Foo"})
	 {:rs-message {:response "HELLO FOO"
		       :uri "/rs3/comp1"
		       :name "Foo"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/comp1" {:value "Foo"})
	 {:rs-message {:response "HELLO WORLD"
		       :uri "/rs3/comp1"
		       :value "Foo"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/comp2")
	 {:rs-message {:response "H-E-L-L-O- -W-O-R-L-D"
		       :uri "/rs3/comp2"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/comp3")
	 {:rs-message {:response "Hello T-O---U-P-P-E-R"
		       :uri "/rs3/comp3"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/comp4")
	 {:rs-message {:response "h-e-l-l-o- -w-o-r-l-d",
		       :uri "/rs3/comp4"
		       :instance-name "rs3"}})))

(deftest parallel-composed-services-test
  (is (= (into #{} (-> (proxy/get-request "http://localhost:8888/rs3/parallel") :rs-message :response))
	   #{"Log: Logged: /rs3/parallel" "Hello world" "to-lower"}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/mapreduce")
	 {:rs-message {:response "Hello world:Log: Logged: /rs3/mapreduce:to-lower"
		       :instance-name "rs3"
		       :uri "/rs3/mapreduce"}})))

(deftest echo-service
  (is (= (proxy/get-request "http://localhost:8888/rs3/echo" {"value" "my-echo-message"})
	 {:rs-message {:response "my-echo-message"
		       :uri "/rs3/echo"
		       :value "my-echo-message"
		       :instance-name "rs3"}})))

(deftest if-then-test-else
  (is (= (proxy/get-request "http://localhost:8888/rs3/test-if-then-else" {"value" "Rob"})
	 {:rs-message {:response "Hello Rob"
		       :uri "/rs3/test-if-then-else"
		       :value "Rob"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/test-if-then-else")
	 {:rs-message {:response "TO-UPPER"
		       :uri "/rs3/test-if-then-else"
		       :instance-name "rs3"}})))

(deftest if-then-test
  (is (= (proxy/get-request "http://localhost:8888/rs3/test-if-then" {"value" "Rob"})
	 {:rs-message {:response "Hello Rob"
		       :uri "/rs3/test-if-then"
		       :value "Rob"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/test-if-then" {"key" "value"})
	 {:rs-message {:key "value"
		       :uri "/rs3/test-if-then"
		       :instance-name "rs3"}}))

  (is (= (proxy/get-request "http://localhost:8888/rs3/test-if-then-true")
	 {:rs-message {:response "H-E-L-L-O- -W-O-R-L-D"
		       :uri "/rs3/test-if-then-true"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/test-if-then-false" {"value" "foobar"})
	 {:rs-message {:response "f-o-o-b-a-r"
		       :uri "/rs3/test-if-then-false"
		       :value "foobar"
		       :instance-name "rs3"}}))

  (is (= (proxy/get-request "http://localhost:8888/rs3/test-if-then-true-comb")
	 {:rs-message {:response "L-o-g-:- -L-o-g-g-e-d-:- -/-r-s-3-/-t-e-s-t---i-f---t-h-e-n---t-r-u-e---c-o-m-b"
		       :instance-name "rs3"
		       :uri "/rs3/test-if-then-true-comb"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/test-if-then-false-comb" {"value" "foobar"})
	 {:rs-message {:response "L-o-g-:- -L-o-g-g-e-d-:- -/-r-s-3-/-t-e-s-t---i-f---t-h-e-n---f-a-l-s-e---c-o-m-b", :instance-name "rs3", :uri "/rs3/test-if-then-false-comb", :value "foobar"}})))

(deftest deadline-test
  (is (= (proxy/get-request "http://localhost:8888/rs3/deadline"
			    {"sleep" 500})
	 {:rs-message {:response "Deadline for monitored service has elapsed."
		       :uri "/rs3/deadline"
		       :sleep "500"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/deadline"
			    {"sleep" 50})
	 {:rs-message {:response "long-running-service result slept for 50 milliseconds."
		       :uri "/rs3/deadline"
		       :sleep "50"
		       :instance-name "rs3"}})))

(deftest app-list-test
  (is (= (proxy/get-request "http://localhost:8888/rs3/apps")
	 {:rs-message {:response "Result from function 0: :Result from function 1: :Result from function 2: :Result from function 3: :Result from function 4: :Result from function 5: :Result from function 6: :Result from function 7: :Result from function 8: :Result from function 9: "
		       :uri "/rs3/apps"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/map-apps" {"value" "foobar"})
	 {:rs-message {:response "Result from function 0: foobar:Result from function 1: foobar:Result from function 2: foobar:Result from function 3: foobar:Result from function 4: foobar:Result from function 5: foobar:Result from function 6: foobar:Result from function 7: foobar:Result from function 8: foobar:Result from function 9: foobar"
		       :uri "/rs3/map-apps"
		       :value "foobar"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/app-select" {"index" 0
								"value" "foobar"})
	 {:rs-message {:response "Result from function 0: foobar"
		       :uri "/rs3/app-select"
		       :value "foobar"
		       :index "0"
		       :instance-name "rs3"}}))
  (is (= (proxy/get-request "http://localhost:8888/rs3/apps" {"index" 0 "value" "foobar"})
	 {:rs-message {:response "Result from function 0: "
		       :uri "/rs3/apps"
		       :value "foobar"
		       :index "0"
		       :instance-name "rs3"}})))
