(defproject ring-dynamic-proxy "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.4.0"]
                 [org.clojure/tools.trace "0.7.9"]
                 [ring/ring-core "1.6.3"]
                 [puppetlabs/http-client "0.9.0"]]
  :profiles {:dev {:dependencies [[ring/ring-jetty-adapter "1.6.3"]
                                  [clj-http "3.7.0"]]}})
