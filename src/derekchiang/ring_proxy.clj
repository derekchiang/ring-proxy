(ns derekchiang.ring-proxy
  (:require [clojure.tools.logging :as log]
            [clojure.string :refer [join split replace-first]]
            [ring.middleware.cookies :as cookies]
            [puppetlabs.http.client.sync :refer [request]])
  (:import (java.net URI)
           (java.util.regex Pattern)))

(defn strip-trailing-slash
  [url]
  (if (.endsWith url "/")
    (.substring url 0 (- (count url) 1))
    url))

(defn prepare-cookies
  "Removes the :domain and :secure keys and converts the :expires key (a Date)
  to a string in the ring response map resp. Returns resp with cookies properly
  munged."
  [resp]
  (let [prepare #(-> (update-in % [1 :expires] str)
                     (update-in [1] dissoc :domain :secure))]
    (assoc resp :cookies (into {} (map prepare (:cookies resp))))))

(defn proxy-request
  [req proxied-path remote-uri-base & [http-opts]]
  ; Remove :decompress-body from the options map, as if this is
  ; ever set to true, the response returned to the client making the
  ; proxy request will be truncated
  (let [http-opts (dissoc http-opts :decompress-body)
        uri (URI. (strip-trailing-slash remote-uri-base))
        remote-uri (URI. (.getScheme uri)
                         (.getAuthority uri)
                         (str (.getPath uri)
                              (if (instance? java.util.regex.Pattern proxied-path)
                                (:uri req)
                                (replace-first (:uri req) proxied-path "")))
                         nil
                         nil)
        response (-> (merge {:method          (:request-method req)
                             :url             (str remote-uri "?" (:query-string req))
                             :headers         (dissoc (:headers req) "host" "content-length")
                             :body            (not-empty (slurp (:body req)))
                             :as              :stream
                             :force-redirects false
                             :follow-redirects false
                             :decompress-body false}
                            http-opts)
                     request
                     prepare-cookies)]
    (log/debugf "Proxying request to %s to remote url %s. Remote server responded with status %s" (:uri req) (str remote-uri) (:status response))
    response))

(defn wrap-proxy
  "Proxies requests to proxied-path, a local URI, to the remote URI at
  remote-uri-base, also a string."
  [handler
   proxied-path
   remote-uri-base
   & [http-opts]]
  (let [proxied-path (if (instance? Pattern proxied-path)
                       (re-pattern (str "^" (.pattern proxied-path)))
                       proxied-path)]
    (cookies/wrap-cookies
     (fn [req]
       (if (or (and (string? proxied-path)
                    (or (.startsWith (:uri req) (str proxied-path "/"))
                        (= (:uri req) proxied-path)))
               (and (instance? Pattern proxied-path) (re-find proxied-path (:uri req))))
         (proxy-request req proxied-path remote-uri-base http-opts)
         (handler req))))))

(def proxy-opts (atom {}))

(defn add-dynamic-proxy
  "Add a proxy.

  The proxy is identified by `id` which can then be used to remove the proxy.

  `args` are the arguments that you would normally pass to `wrap-proxy`, minus
  the `handler` argument."
  [id & args]
  (swap! proxy-opts assoc id args))

(defn remove-dynamic-proxy
  "Remove the proxy identified by `id`. If the proxy doesn't exist, nothing
  happens."
  [id]
  (swap! proxy-opts dissoc id))

(defn clear-dynamic-proxies
  "Remove all dynamic proxies that have been added via `add-dynamic-proxy`."
  []
  (reset! proxy-opts {}))

(defn- apply-wrap-proxy [handler opts]
  (apply wrap-proxy handler opts))

(defn wrap-dynamic-proxy
  "A Ring middleware that adds a set of dynamically configured proxies. The
  proxies are added and removed via `add-dynamic-proxy` and
  `remove-dynamic-proxy`."
  [handler]
  (fn [req]
    ((->> @proxy-opts
          vals
          (reduce apply-wrap-proxy handler)) req)))

