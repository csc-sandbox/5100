package edu.calpoly.lambdalab.instructor;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Consumes A04 SQS records, validates event identifiers and payloads, resolves MQTT topics, and forwards events to configured AWS IoT Core and HiveMQ publishers.
 *
 * @author Javier Gonzalez-Sanchez
 * @version 2.0 (2026-10-29)
 */
public class EventProcessorLambda implements RequestHandler<Map<String, Object>, Void> {
  private final ObjectMapper mapper = new ObjectMapper();
  private final BiConsumer<String, String> publisher;
  private final BiConsumer<String, String> hiveMqPublisher;

  public EventProcessorLambda() {
    this(System.getenv("A04_IOT_ENDPOINT") == null
        ? null : new AwsIotEventPublisher()::publish,
        "true".equalsIgnoreCase(System.getenv("A04_HIVEMQ_ENABLED"))
            ? new HiveMqEventPublisher()::publish : null);
  }

  EventProcessorLambda(BiConsumer<String, String> publisher,
      BiConsumer<String, String> hiveMqPublisher) {
    this.publisher = publisher;
    this.hiveMqPublisher = hiveMqPublisher;
  }

  @Override
  public Void handleRequest(Map<String, Object> event, Context context) {
    if (event == null || !(event.get("Records") instanceof List<?> records)) {
      throw new IllegalArgumentException("Expected SQS Records");
    }
    for (Object record : records) {
      if (!(record instanceof Map<?, ?> entry) || !(entry.get("body") instanceof String body)) {
        throw new IllegalArgumentException("Invalid SQS record");
      }
      try {
        JsonNode json = mapper.readTree(body);
        String id = json.path("id").asText();
        if (!UUID.fromString(id).toString().equals(id)) {
          throw new IllegalArgumentException("Invalid correlation id");
        }
        if (!json.hasNonNull("result")) throw new IllegalArgumentException("Missing result");
        String topic = A04MqttTopics.forEvent(json);
        log(context, "A04 SQS received id=" + id + " topic=" + topic);
        RuntimeException publishError = null;
        if (publisher != null) {
          try {
            publisher.accept(topic, body);
            log(context, "A04 IoT published id=" + id + " topic=" + topic);
          } catch (RuntimeException e) {
            log(context, "A04 IoT publish FAILED id=" + id + ": " + e);
            publishError = e;
          }
        }
        if (hiveMqPublisher != null) {
          try {
            hiveMqPublisher.accept(topic, body);
            log(context, "A04 HiveMQ published id=" + id + " topic=" + topic);
          } catch (RuntimeException e) {
            log(context, "A04 HiveMQ publish FAILED id=" + id + ": " + e);
            if (publishError == null) publishError = e;
            else publishError.addSuppressed(e);
          }
        }
        if (publishError != null) throw publishError;
      } catch (Exception e) {
        throw new IllegalArgumentException("SQS event processing failed; retry expected", e);
      }
    }
    return null;
  }

  private static void log(Context context, String message) {
    if (context != null) context.getLogger().log(message);
    else System.out.println(message);
  }
}
