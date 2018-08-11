(ns puppetlabs.services.protocols.request-handler)

(defprotocol RequestHandlerService
  (handle-request
    [this request]
    "Handles a request from an agent.")
  (get-wrapped-handler
    [this handler]
    "Wraps a Ring handler with JRuby middleware. The handler will be passed
    two arguments:

      - A Ring request map that contains interfaces to the JRuby interpreter
      - A mutable request map that has been transformed for Ruby

    The handler must return a JRubyPuppetResponse instance."))
