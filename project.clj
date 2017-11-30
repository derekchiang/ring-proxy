(defproject derekchiang/ring-proxy "1.0.1"
  :description "Dynamic and static proxy for Ring"
  :url "https://github.com/derekchiang/ring-proxy"
  :license {:name "Apache License v2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.4.0"]
                 [ring/ring-core "1.6.3"]
                 [puppetlabs/http-client "0.9.0"]]
  :profiles {:dev {:dependencies [[ring/ring-jetty-adapter "1.6.3"]
                                  [clj-http "3.7.0"]
                                  [org.clojure/tools.trace "0.7.9"]]}}
  :deploy-repositories [["releases" :clojars]])
