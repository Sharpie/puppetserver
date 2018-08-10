(ns puppetlabs.puppetserver.jruby-request
  (:import [clojure.lang IFn])
  (:require [clojure.tools.logging :as log]
            [ring.util.response :as ring-response]
            [slingshot.slingshot :as sling]
            [puppetlabs.ring-middleware.core :as middleware]
            [puppetlabs.ring-middleware.utils :as ringutils]
            [puppetlabs.services.protocols.jruby-puppet :as jruby-puppet]
            [puppetlabs.services.protocols.jruby-metrics :as jruby-metrics]
            [puppetlabs.services.jruby-pool-manager.jruby-core :as jruby-core]
            [schema.core :as schema]
            [puppetlabs.puppetserver.common :as ps-common]
            [puppetlabs.i18n.core :as i18n]))

(defn jruby-timeout?
  "Determine if the supplied slingshot message is for a JRuby borrow timeout."
  [x]
  (when (map? x)
    (= (:kind x)
       ::jruby-core/jruby-timeout)))

(defn output-error
  [{:keys [uri]} {:keys [msg]} http-status]
  (log/error (i18n/trs "Error {0} on SERVER at {1}: {2}" http-status uri msg))
  (ringutils/plain-response http-status msg))

(defn wrap-with-error-handling
  "Middleware that wraps a JRuby request with some error handling to return
  the appropriate http status codes, etc."
  [handler]
  (fn [request]
    (sling/try+
     (handler request)
     (catch ringutils/bad-request? e
       (output-error request e 400))
     (catch jruby-timeout? e
       (output-error request e 503))
     (catch ringutils/service-unavailable? e
       (output-error request e 503)))))

(schema/defn ^:always-validate wrap-with-jruby-instance :- IFn
  "Middleware fn that borrows a jruby instance from the `jruby-service` and makes
  the `com.puppetlabs.puppetserver.JRubyPuppet` interface available in the request
  map as `:jruby-instance` and the `com.puppetlabs.jruby_utils.jrubyScriptingContainer`
  interface available as `:jruby-container`."
  [handler :- IFn
   jruby-service :- (schema/protocol jruby-puppet/JRubyPuppetService)]
  (fn [request]
    (let [jruby-pool (jruby-puppet/get-pool-context jruby-service)]
      (let [borrow-reason {:request (dissoc request :ssl-client-cert)}]
        (jruby-core/with-jruby-instance jruby-instance jruby-pool borrow-reason
          (-> request
              (assoc :jruby-instance (:jruby-puppet jruby-instance))
              (assoc :jruby-container (:scripting-container jruby-instance))
              handler))))))

(schema/defn ^:always-validate
  wrap-with-request-queue-limit :- IFn
  "Middleware fn that short-circuits incoming requests with a 503 'Service
  Unavailable' response if the queue of outstanding requests for JRuby
  instances exceeds the limit given by the max-queued-requests argument.
  The response will include a Retry-After header set to a random fraction
  of the value given by the max-retry-delay argument if set to a positive
  non-zero number."
  [handler :- IFn
   metrics-service :- (schema/protocol jruby-metrics/JRubyMetricsService)
   max-queued-requests :- schema/Int
   max-retry-delay :- schema/Int]
  (let [metrics-map (jruby-metrics/get-metrics metrics-service)
        {:keys [requested-instances queue-limit-hit-meter]} metrics-map
        err-msg (i18n/trs "The number of requests waiting for a JRuby instance has exceeded the limit allowed by the max-queued-requests setting.")]
    (fn [request]
      (if (>= (count @requested-instances) max-queued-requests)
        (let [response (output-error request {:msg err-msg} 503)]
          ;; Record an occurance of the rate limit being hit to metrics.
          (.mark queue-limit-hit-meter)
          (if (pos? max-retry-delay)
            (assoc-in response [:headers "Retry-After"]
                      (-> (rand) (* max-retry-delay) int str))
            response))
        (handler request)))))

(defn get-environment-from-request
  "Gets the environment from the URL or query string of a request."
  [req]
  ;; If environment is derived from the path, favor that over a query/form
  ;; param named environment, since it doesn't make sense to ask about
  ;; environment production in environment development.
  (or (get-in req [:route-params :environment])
      (get-in req [:params "environment"])))

(defn wrap-with-environment-validation
  "Middleware function which validates the presence and syntactical content
  of an environment in a ring request.  If validation fails, a :bad-request
  slingshot exception is thrown. If the optional-environment-param is
  provided as true, it allows for the endpoint to have its query param
  environment to be optional. However if the param is provided, it will
  still validate that param for the given request."
  ([handler]
   (wrap-with-environment-validation handler false))
  ([handler optional-environment-param]
   (fn [request]
     (let [environment (get-environment-from-request request)]
       (cond
         (and (true? optional-environment-param)
              (nil? environment))
         (handler request)

         (nil? environment)
         (ringutils/throw-bad-request!
           (i18n/tru "An environment parameter must be specified"))

         (not (nil? (schema/check ps-common/Environment environment)))
         (ringutils/throw-bad-request!
           (ps-common/environment-validation-error-msg environment))

         :else
         (handler request))))))
