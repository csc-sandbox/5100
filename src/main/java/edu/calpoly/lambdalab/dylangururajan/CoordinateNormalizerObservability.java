package edu.calpoly.lambdalab.dylangururajan;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Creates one-line JSON events suitable for CloudWatch Logs Insights. */
final class CoordinateNormalizerObservability {
  static final String COURSE = "CSC5100";
  static final String COMPONENT = "CoordinateNormalizerLambda";

  private final ObjectMapper mapper;

  CoordinateNormalizerObservability() {
    this(new ObjectMapper());
  }

  CoordinateNormalizerObservability(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  String event(
      String service,
      String event,
      String status,
      String requestId,
      Long durationMs,
      Throwable failure) {
    ObjectNode logEvent = mapper.createObjectNode();
    logEvent.put("course", COURSE);
    logEvent.put("component", COMPONENT);
    logEvent.put("service", service);
    logEvent.put("event", event);
    logEvent.put("status", status);
    if (requestId != null && !requestId.isBlank()) {
      logEvent.put("requestId", requestId);
    }
    if (durationMs != null) {
      logEvent.put("durationMs", Math.max(0, durationMs));
    }
    if (failure != null) {
      // The exception type identifies the failure without risking payload or credential leakage.
      logEvent.put("errorType", failure.getClass().getSimpleName());
    }
    return logEvent.toString();
  }

  void log(
      LambdaLogger logger,
      String service,
      String event,
      String status,
      String requestId,
      long durationMs,
      Throwable failure) {
    if (logger != null) {
      logger.log(event(service, event, status, requestId, durationMs, failure) + System.lineSeparator());
    }
  }
}
