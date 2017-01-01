require 'puppet/util/tracing'
require 'java'

module Puppet::Util
  module Tracing
    class JVMTracer < OpenTracing::Tracer
      def initialize(tracer)
        @jvm_tracer = tracer
        @span_stack = []
      end

      def current_span
        @span_stack.last
      end

      # Start a new span
      # @param operation_name [String] The name of the operation represented by the span
      # @param child_of [Span] A span to be used as the ChildOf reference
      # @param start_time [Time] the start time of the span
      # @param tags [Hash] Starting tags for the span
      def start_span(operation_name, child_of: self.current_span, start_time: nil, tags: Hash.new)
        builder = @jvm_tracer.buildSpan(operation_name.to_java)

        builder.asChildOf(child_of) unless child_of.nil?
        builder.withStartTimestamp((start_time.to_f * 1E6).to_i.to_java) unless start_time.nil?

        tags.each do |k, v|
          builder.withTag(k.to_s.to_java, v.to_java)
        end

        span = JVMSpan.new(self, builder.start)

        @span_stack.push(span)

        span
      end

      def inject(span_context, format, carrier)
        case format
        when OpenTracing::FORMAT_TEXT_MAP
          @jvm_tracer.inject(span_context,
            Java::IoOpentracingPropagation::Format::Builtin::TEXT_MAP,
            Java::IoOpentracingPropagation::TextMapInjectAdapter.new(carrier.to_java))
        else
          # Not implementing binary or other formats at this time.
        end

        nil
      end

      def extract(operation_name, format, carrier)
        context = case format
        when OpenTracing::FORMAT_TEXT_MAP
          @jvm_tracer.extract(Java::IoOpentracingPropagation::Format::Builtin::TEXT_MAP,
            # TODO: Might have to stringify Ruby hash keys.
            Java::IoOpentracingPropagation::TextMapExtractAdapter.new(carrier.to_java))
        else
          # Not implementing binary or other formats at this time.
          nil
        end

        return OpenTracing::Span::NOOP_INSTANCE if context.nil?

        start_span(operation_name, child_of: context, tags: {"span.kind" => "server"})
      end

      def finish_span(span)
        @span_stack.delete(span)
      end
    end

    class JVMSpan < OpenTracing::Span
      attr_reader :jvm_span


      # @param context [Java::IoOpentracing::Span] An OpenTracing Java span.
      def initialize(tracer, context)
        @tracer = tracer
        @jvm_span = context
      end

      def context
        @jvm_span.context
      end

      def set_tag(key, value)
        @jvm_span.setTag(key.to_java, value.to_java)

        self
      end

      def set_baggage_item(key, value)
        # Not implementing baggage.
        self
      end

      def get_baggage_item(key, value)
        nil
      end

      def log(event: nil, timestamp: nil, **fields)
        if timestamp.nil?
          @jvm_span.log(event.to_java)
        else
          @jvm_span.log((timestamp.to_f * 1E6).to_i.to_java, event.to_java)
        end

        self
      end

      def finish(end_time: Time.now)
        @tracer.finish_span(self)
        @jvm_span.finish

        self
      end
    end
  end
end
