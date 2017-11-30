(ns derekchiang.ring-proxy.core-test
  (:require [clojure.test :refer :all]
            [ring.adapter.jetty :refer [run-jetty]]
            [clj-http.client :as client]
            [derekchiang.ring-proxy.core :refer :all]))

(defn proxy-handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "proxy"})

(defn proxied-handler [request]
  {:status 200
   :headers {"Content-Type" "text/html"}
   :body "proxied"})

(defn setup-servers [f]
  ;; Start the proxied server in another thread.
)

;; (use-fixtures :each setup-servers)

(deftest test-proxy
  (let [proxy (-> proxy-handler
                (wrap-proxy "/" "http://localhost:3001")
                (run-jetty {:port 3000
                            :join? false}))
        proxied (run-jetty proxied-handler {:port 3001
                                            :join? false})
        response (client/get "http://localhost:3000")]
    (try
      (is (and (= (:body response) "proxied")
               (= (:status response) 200))
          (str response))
      (finally (do (.stop proxy)
                   (.stop proxied))))))

(deftest test-dynamic-proxy
  (let [proxy (-> proxy-handler
                wrap-dynamic-proxy
                (run-jetty {:port 3000
                            :join? false}))
        proxied (run-jetty proxied-handler {:port 3001
                                            :join? false})]
    (try
      (testing "before adding proxy"
        (let [response (client/get "http://localhost:3000")]
          (is (and (= (:body response) "proxy")
                   (= (:status response) 200))
              (str response))))
      (testing "after adding proxy"
        (add-dynamic-proxy :first "/" "http://localhost:3001")
        (let [response (client/get "http://localhost:3000")]
          (is (and (= (:body response) "proxied")
                   (= (:status response) 200))
              (str response))))
      (testing "after removing proxy"
        (remove-dynamic-proxy :first)
        (let [response (client/get "http://localhost:3000")]
          (is (and (= (:body response) "proxy")
                   (= (:status response) 200))
              (str response))))
      (finally (do
                 (.stop proxy)
                 (.stop proxied)
                 (clear-dynamic-proxies))))))
