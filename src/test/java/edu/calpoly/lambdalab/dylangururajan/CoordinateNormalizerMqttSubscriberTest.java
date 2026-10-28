package edu.calpoly.lambdalab.dylangururajan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoordinateNormalizerMqttSubscriberTest {
  private static final String ID = "14caa8ee-ca63-41f4-b57f-074007b2affc";
  private static final String EVENT = "{\"id\":\"" + ID
      + "\",\"operation\":\"CoordinateNormalizerLambda\","
      + "\"result\":{\"normalizedX\":0.25,\"normalizedY\":0.75}}";

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void displaysEventAndPublishesCorrelatedAcknowledgment() throws Exception {
    List<String> display = new ArrayList<>();
    List<String> acknowledgments = new ArrayList<>();
    CoordinateNormalizerMqttSubscriber subscriber =
        new CoordinateNormalizerMqttSubscriber("tcp://unused", display::add);

    CoordinateNormalizerMqttSubscriber.ProcessedEvent processed =
        subscriber.handleEvent(
            EVENT, (topic, json) -> acknowledgments.add(topic + "|" + json));

    assertEquals(ID, processed.id());
    assertEquals(1, display.size());
    assertTrue(display.get(0).contains("normalizedX=0.250000"));
    assertTrue(display.get(0).contains("normalizedY=0.750000"));
    assertEquals(1, acknowledgments.size());

    String[] published = acknowledgments.get(0).split("\\|", 2);
    assertEquals("csc5100/status/" + ID, published[0]);
    JsonNode acknowledgment = mapper.readTree(published[1]);
    assertEquals(ID, acknowledgment.get("id").asText());
    assertEquals("CoordinateNormalizerLambda", acknowledgment.get("operation").asText());
    assertEquals("received", acknowledgment.get("status").asText());
  }

  @Test
  void rejectsInvalidUuidWrongOperationAndMissingResult() {
    CoordinateNormalizerMqttSubscriber subscriber =
        new CoordinateNormalizerMqttSubscriber("tcp://unused", ignored -> { });

    assertThrows(IllegalArgumentException.class,
        () -> subscriber.parseEvent(EVENT.replace(ID, "bad")));
    assertThrows(IllegalArgumentException.class,
        () -> subscriber.parseEvent(
            EVENT.replace("CoordinateNormalizerLambda", "TemperatureLambda")));
    assertThrows(IllegalArgumentException.class,
        () -> subscriber.parseEvent("{\"id\":\"" + ID
            + "\",\"operation\":\"CoordinateNormalizerLambda\",\"result\":{}}"));
  }
}
