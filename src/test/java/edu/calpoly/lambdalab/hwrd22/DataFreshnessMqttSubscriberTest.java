package edu.calpoly.lambdalab.hwrd22;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DataFreshnessMqttSubscriberTest {
    private static final String ID = "6d05f154-023b-4c84-8e4c-6fcd90aa0d08";
    private static final String EVENT = "{\"id\":\"" + ID
            + "\",\"operation\":\"DataFreshnessLambda\","
            + "\"result\":{\"status\":\"FRESH\",\"age\":5}}";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void displayEventAndPublishCorrelatedAcknowledgement() throws Exception {
        List<String> display = new ArrayList<>();
        List<String> acknowledgements = new ArrayList<>();
        DataFreshnessMqttSubscriber subscriber = new DataFreshnessMqttSubscriber("tcp://unused", display::add);

        DataFreshnessMqttSubscriber.ProcessedEvent processedEvent = subscriber.handleEvent(EVENT, (topic, json) -> acknowledgements.add(topic + "|" + json));

        assertEquals(ID, processedEvent.id());
        assertEquals(1, display.size());
        assertTrue(display.get(0).contains("Status = FRESH"));
        assertTrue(display.get(0).contains("Age = 5"));
        assertEquals(1, acknowledgements.size());

        String[] published = acknowledgements.get(0).split("\\|", 2);
        assertEquals("csc5100/status/" + ID, published[0]);
        JsonNode acknowledgement = mapper.readTree(published[1]);
        assertEquals(ID, acknowledgement.get("id").asText());
        assertEquals("DataFreshnessLambda", acknowledgement.get("operation").asText());
        assertEquals("RECEIVED", acknowledgement.get("status").asText());
    }

    @Test
    void rejectsInvalidUuidWrongOperationAndMissingResult() {
        DataFreshnessMqttSubscriber subscriber = new DataFreshnessMqttSubscriber("tcp://unused", ignored -> { });

        assertThrows(IllegalArgumentException.class, () ->
                subscriber.parseEvent(EVENT.replace(ID, "invalid_id")));
        assertThrows(IllegalArgumentException.class, () ->
                subscriber.parseEvent(EVENT.replace("DataFreshnessLambda", "TemperatureLambda")));
        assertThrows(IllegalArgumentException.class, () ->
                subscriber.parseEvent("{\"id\":\"" + ID
                        + "\",\"operation\":\"DataFreshnessLambda\",\"result\":{}}"));
    }
}
