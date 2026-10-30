package edu.calpoly.lambdalab.instructor;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Verifies the public dashboard's JSON event contract.
 * @author Javier Gonzalez-Sanchez
 * @version 1.0
 */
class TemperatureObservabilityTest {
  @Test
  void emitsQueryFriendlyJsonWithoutPayload() throws Exception {
    PrintStream previous = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
      TemperatureObservability.log("c5f1b1b7-ccf5-4b98-80f9-f1123589a123",
          "lambda-request-1", "S3", "S3_SAVED", "SUCCESS", System.nanoTime());
    } finally {
      System.setOut(previous);
    }
    var event = new ObjectMapper().readTree(captured.toString(StandardCharsets.UTF_8).trim());
    assertEquals("CSC5100", event.get("course").asText());
    assertEquals("S3", event.get("service").asText());
    assertEquals("S3_SAVED", event.get("event").asText());
    assertEquals("SUCCESS", event.get("status").asText());
    assertTrue(event.has("readingId"));
    assertTrue(event.has("requestId"));
    assertTrue(event.get("durationMs").asDouble() >= 0);
    assertFalse(event.has("payload"));
  }
}
