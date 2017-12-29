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

(defn- apply-wrap-proxy [handler opts]
  (apply wrap-proxy handler opts))

(defn dynamic-proxy
  "Return functions `wrap`, `add`, `remove`, and `clear`, where:

  * `wrap` is a ring middleware that adds dynamic proxying behavior.

  * `add` adds a new proxy.  It takes `[id & args]`, where `id` identifies the
  proxy, and `args` are the arguments that you would normally pass to
  `wrap-proxy`, minus the `handler` argument.

  * `remove` removes a proxy.  It takes `id` which identifies the proxy to be
  removed.

  * `clear` removes all existing proxies."
  []
  (let [;; A map from IDs to proxies
        *proxies* (atom {})
        wrap (fn [handler]
               (fn [req]
                 ((->> @*proxies*
                       vals
                       (reduce apply-wrap-proxy handler)) req)))
        add (fn [id & args]
              (swap! *proxies* assoc id args))
        remove (fn [id]
                 (swap! *proxies* dissoc id))
        clear (fn []
                (reset! *proxies* {}))]
    [wrap add remove clear]))

