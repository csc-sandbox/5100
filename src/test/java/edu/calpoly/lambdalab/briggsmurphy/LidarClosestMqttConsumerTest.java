package edu.calpoly.lambdalab.briggsmurphy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LidarClosestMqttConsumerTest {

    @Test
    void parsesValidEvent() throws Exception {
        String json = """
                {
                  "id": "test-123",
                  "operation": "LidarClosestLambda",
                  "result": {"distance": 12.5}
                }
                """;

        JsonNode event = LidarClosestMqttConsumer.parseEvent(json);

        assertEquals("test-123", event.get("id").asText());
        assertEquals(
                "LidarClosestLambda",
                event.get("operation").asText()
        );
        assertEquals(12.5, event.get("result").get("distance").asDouble());
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(
                Exception.class,
                () -> LidarClosestMqttConsumer.parseEvent("{not-json")
        );
    }

    @Test
    void rejectsEventWithoutId() {
        String json = """
                {
                  "operation": "LidarClosestLambda",
                  "result": {}
                }
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> LidarClosestMqttConsumer.parseEvent(json)
        );
    }

    @Test
    void rejectsEventWithoutOperation() {
        String json = """
                {
                  "id": "test-123",
                  "result": {}
                }
                """;

        assertThrows(
                IllegalArgumentException.class,
                () -> LidarClosestMqttConsumer.parseEvent(json)
        );
    }

    @Test
    void createsAcknowledgmentTopicFromId() {
        assertEquals(
                "csc5100/status/test-123",
                LidarClosestMqttConsumer.acknowledgmentTopic("test-123")
        );
    }

    @Test
    void rejectsEmptyAcknowledgmentId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LidarClosestMqttConsumer.acknowledgmentTopic("")
        );
    }

    @Test
    void createsAcknowledgmentJson() throws Exception {
        String json =
                LidarClosestMqttConsumer.acknowledgmentPayload("test-123");

        JsonNode acknowledgment = new ObjectMapper().readTree(json);

        assertEquals("test-123", acknowledgment.get("id").asText());
        assertEquals("ACK", acknowledgment.get("status").asText());
    }

    @Test
    void rejectsEmptyAcknowledgmentPayloadId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> LidarClosestMqttConsumer.acknowledgmentPayload(" ")
        );
    }
}
