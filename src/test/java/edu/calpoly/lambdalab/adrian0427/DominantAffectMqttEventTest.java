
package edu.calpoly.lambdalab.adrian0427;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DominantAffectMqttEventTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesValidEvent() throws Exception {
        String id = UUID.randomUUID().toString();

        DominantAffectMqttEvent.Event event =
                DominantAffectMqttEvent.parse(createEventJson(id));

        assertEquals(id, event.id());
        assertEquals("joy", event.metric());
        assertEquals(0.9, event.value(), 0.0001);
    }

    @Test
    void createsCorrectAckTopic() throws Exception {
        String id = UUID.randomUUID().toString();

        DominantAffectMqttEvent.Event event =
                DominantAffectMqttEvent.parse(createEventJson(id));

        assertEquals(
                "csc5100/status/" + id,
                DominantAffectMqttEvent.ackTopic(event.id()));
    }

    @Test
    void createsValidAckPayload() throws Exception {
        String id = UUID.randomUUID().toString();

        DominantAffectMqttEvent.Event event =
                DominantAffectMqttEvent.parse(createEventJson(id));

        String ackJson = DominantAffectMqttEvent.ackJson(event);
        Map<?, ?> ack = mapper.readValue(ackJson, Map.class);

        assertEquals(id, ack.get("id"));
        assertEquals("DominantAffectLambda", ack.get("operation"));
        assertEquals("received", ack.get("status"));
    }

    @Test
    void rejectsInvalidJson() {
        assertThrows(IllegalArgumentException.class,
                () -> DominantAffectMqttEvent.parse("{invalid-json}"));
    }

    @Test
    void rejectsMissingUuid() {
        String json = """
                {
                  "operation": "DominantAffectLambda",
                  "result": {
                    "metric": "joy",
                    "value": 0.9
                  }
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> DominantAffectMqttEvent.parse(json));
    }

    @Test
    void rejectsMalformedUuid() {
        String json = """
                {
                  "id": "not-a-uuid",
                  "operation": "DominantAffectLambda",
                  "result": {
                    "metric": "joy",
                    "value": 0.9
                  }
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> DominantAffectMqttEvent.parse(json));
    }

    @Test
    void rejectsIncorrectOperation() throws Exception {
        String json = mapper.writeValueAsString(Map.of(
                "id", UUID.randomUUID().toString(),
                "operation", "TemperatureLambda",
                "result", Map.of(
                        "metric", "joy",
                        "value", 0.9)));

        assertThrows(IllegalArgumentException.class,
                () -> DominantAffectMqttEvent.parse(json));
    }

    @Test
    void rejectsMissingResult() throws Exception {
        String json = mapper.writeValueAsString(Map.of(
                "id", UUID.randomUUID().toString(),
                "operation", "DominantAffectLambda"));

        assertThrows(IllegalArgumentException.class,
                () -> DominantAffectMqttEvent.parse(json));
    }

    @Test
    void rejectsNonNumericValue() throws Exception {
        String json = mapper.writeValueAsString(Map.of(
                "id", UUID.randomUUID().toString(),
                "operation", "DominantAffectLambda",
                "result", Map.of(
                        "metric", "joy",
                        "value", "high")));

        assertThrows(IllegalArgumentException.class,
                () -> DominantAffectMqttEvent.parse(json));
    }

    private String createEventJson(String id) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "id", id,
                "operation", "DominantAffectLambda",
                "result", Map.of(
                        "metric", "joy",
                        "value", 0.9)));
    }
}
