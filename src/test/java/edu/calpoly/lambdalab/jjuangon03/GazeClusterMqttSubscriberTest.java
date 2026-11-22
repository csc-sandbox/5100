package edu.calpoly.lambdalab.jjuangon03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GazeClusterMqttSubscriberTest {
    private static final String ID = "14caa8ee-ca63-41f4-b57f-074007b2affc";
    private static final String EVENT = "{\"id\":\"" + ID + "\",\"operation\":\"GazeClusterLambda\","
            + "\"result\":{\"numberOfClusters\":1,\"clusters\":[{\"id\":0,\"centerX\":0.5,"
            + "\"centerY\":0.5,\"points\":3}]}}";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void displaysEventAndPublishesUuidSpecificAcknowledgment() throws Exception {
        List<String> display = new ArrayList<>();
        List<String> publications = new ArrayList<>();
        GazeClusterMqttSubscriber subscriber = new GazeClusterMqttSubscriber("tcp://unused", display::add);

        GazeClusterMqttSubscriber.ProcessedEvent event = subscriber.handleEvent(
                EVENT, (topic, json) -> publications.add(topic + "|" + json));

        assertEquals(ID, event.id());
        assertTrue(display.get(0).contains("1 cluster(s)"));
        String[] publication = publications.get(0).split("\\|", 2);
        assertEquals("csc5100/status/" + ID, publication[0]);
        JsonNode acknowledgment = mapper.readTree(publication[1]);
        assertEquals(ID, acknowledgment.path("id").asText());
        assertEquals("GazeClusterLambda", acknowledgment.path("operation").asText());
        assertEquals("received", acknowledgment.path("status").asText());
    }

    @Test
    void rejectsUntrustedOrInconsistentEvents() {
        GazeClusterMqttSubscriber subscriber = new GazeClusterMqttSubscriber("tcp://unused", ignored -> { });
        assertThrows(IllegalArgumentException.class, () -> subscriber.parseEvent(EVENT.replace(ID, "bad")));
        assertThrows(IllegalArgumentException.class,
                () -> subscriber.parseEvent(EVENT.replace("GazeClusterLambda", "OtherLambda")));
        assertThrows(IllegalArgumentException.class,
                () -> subscriber.parseEvent(EVENT.replace("\"numberOfClusters\":1", "\"numberOfClusters\":2")));
    }
}
