package edu.calpoly.lambdalab.dylangururajan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CoordinateNormalizerObservabilityTest {
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void emitsStructuredJsonWithRequiredCorrelationAndDurationFields() throws Exception {
    CapturingLogger logger = new CapturingLogger();
    CoordinateNormalizerObservability observability =
        new CoordinateNormalizerObservability();

    observability.log(
        logger,
        "Storage",
        "RESULT_SAVED",
        "SUCCESS",
        "request-abc-123",
        7,
        null);

    JsonNode event = mapper.readTree(logger.output().trim());
    assertEquals("CSC5100", event.get("course").asText());
    assertEquals("CoordinateNormalizerLambda", event.get("component").asText());
    assertEquals("Storage", event.get("service").asText());
    assertEquals("RESULT_SAVED", event.get("event").asText());
    assertEquals("SUCCESS", event.get("status").asText());
    assertEquals("request-abc-123", event.get("requestId").asText());
    assertTrue(event.get("durationMs").isNumber());
    assertTrue(event.get("durationMs").asLong() >= 0);
  }

  @Test
  void failureLogIdentifiesExceptionWithoutLeakingSensitiveData() throws Exception {
    String sensitive = "secret-access-key=do-not-log request-body=private-data";
    CoordinateNormalizerObservability observability =
        new CoordinateNormalizerObservability();

    String json = observability.event(
        "Messaging",
        "RESULT_PUBLISHED",
        "FAILURE",
        "request-failed-456",
        0L,
        new RuntimeException(sensitive));

    JsonNode event = mapper.readTree(json);
    assertEquals("RuntimeException", event.get("errorType").asText());
    assertFalse(json.contains("do-not-log"));
    assertFalse(json.contains("private-data"));
    assertFalse(json.contains("secret-access-key"));
  }

  private static final class CapturingLogger implements LambdaLogger {
    private final StringBuilder output = new StringBuilder();

    @Override
    public void log(String message) {
      output.append(message);
    }

    @Override
    public void log(byte[] message) {
      output.append(new String(message, StandardCharsets.UTF_8));
    }

    String output() {
      return output.toString();
    }
  }
}
