package edu.calpoly.lambdalab.paulmotter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for event parsing and ACK logic. No broker is used.
 *
 * @author Paul Motter (PaulMotter)
 * @version 1.0 (10/21/2026)
 */
class RobotDistanceEventHandlerTest {
    private static final String ID = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
    private static final String VALID =
        "{\"id\":\"" + ID + "\",\"operation\":\"RobotDistanceLambda\",\"result\":7.0}";
    private static final Instant NOW = Instant.parse("2026-10-21T20:00:00Z");

    private final ObjectMapper mapper = new ObjectMapper();
    private List<String[]> published;
    private List<String> displayed;
    private RobotDistanceEventHandler handler;

    @BeforeEach
    void setUp() {
        published = new ArrayList<>();
        displayed = new ArrayList<>();
        handler = new RobotDistanceEventHandler(
            (topic, json) -> published.add(new String[] {topic, json}),
            displayed::add,
            "test-client",
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void parsesValidEvent() {
        RobotDistanceEvent event = RobotDistanceEvent.parse(VALID);
        assertEquals(ID, event.id());
        assertEquals("RobotDistanceLambda", event.operation());
        assertEquals(7.0, event.result());
    }

    @Test
    void acceptsLambdaAliasAndIntegerResult() {
        RobotDistanceEvent event = RobotDistanceEvent.parse(
            "{\"id\":\"" + ID + "\",\"lambda\":\"RobotDistanceLambda\",\"result\":0}");
        assertEquals(0.0, event.result());
    }

    @Test
    void keepsExtraFieldsForDisplay() {
        RobotDistanceEvent event = RobotDistanceEvent.parse(
            "{\"id\":\"" + ID + "\",\"operation\":\"RobotDistanceLambda\",\"result\":1.5,\"x1\":2.0}");
        assertEquals(2.0, event.raw().get("x1").asDouble());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "not json",
        "[1,2,3]",
        "{\"operation\":\"RobotDistanceLambda\",\"result\":1.0}",
        "{\"id\":\"bad\",\"operation\":\"RobotDistanceLambda\",\"result\":1.0}",
        "{\"id\":\"" + ID + "\",\"operation\":\"TemperatureLambda\",\"result\":1.0}",
        "{\"id\":\"" + ID + "\",\"result\":1.0}",
        "{\"id\":\"" + ID + "\",\"operation\":\"RobotDistanceLambda\"}",
        "{\"id\":\"" + ID + "\",\"operation\":\"RobotDistanceLambda\",\"result\":\"7.0\"}",
        "{\"id\":\"" + ID + "\",\"operation\":\"RobotDistanceLambda\",\"result\":-1.0}"
    })
    void rejectsInvalidEvents(String payload) {
        assertThrows(IllegalArgumentException.class, () -> RobotDistanceEvent.parse(payload));
    }

    @Test
    void rejectsNullPayload() {
        assertThrows(IllegalArgumentException.class, () -> RobotDistanceEvent.parse(null));
    }

    @Test
    void validEventPublishesOneAckToStatusTopic() throws Exception {
        assertTrue(handler.handle(VALID));

        assertEquals(1, published.size());
        assertEquals("csc5100/status/" + ID, published.get(0)[0]);

        JsonNode ack = mapper.readTree(published.get(0)[1]);
        assertEquals(ID, ack.get("id").asText());
        assertEquals("ACK", ack.get("status").asText());
        assertEquals("RobotDistanceLambda", ack.get("operation").asText());
        assertEquals(7.0, ack.get("result").asDouble());
        assertEquals("test-client", ack.get("client").asText());
        assertEquals("2026-10-21T20:00:00Z", ack.get("receivedAt").asText());
    }

    @Test
    void validEventIsDisplayed() {
        handler.handle(VALID);

        String output = String.join("\n", displayed);
        assertTrue(output.contains("[event]"));
        assertTrue(output.contains(ID));
        assertTrue(output.contains("7.0"));
        assertTrue(output.contains("[ack]"));
    }

    @Test
    void invalidEventIsRejectedWithoutAck() {
        assertFalse(handler.handle("{\"id\":\"bad\",\"result\":1}"));

        assertTrue(published.isEmpty());
        assertEquals(1, displayed.size());
        assertTrue(displayed.get(0).startsWith("[rejected]"));
    }

    @Test
    void publishFailureIsReportedNotThrown() {
        RobotDistanceEventHandler failing = new RobotDistanceEventHandler(
            (topic, json) -> { throw new IllegalStateException("broker down"); },
            displayed::add, "test-client", Clock.fixed(NOW, ZoneOffset.UTC));

        assertFalse(failing.handle(VALID));
        assertTrue(displayed.get(displayed.size() - 1).startsWith("[ack failed]"));
    }

    @Test
    void ackMessageUsesQos1() {
        MqttMessage message = RobotDistanceDesktopClient.ackMessage("{\"id\":\"" + ID + "\"}");
        assertEquals(1, message.getQos());
        assertEquals("{\"id\":\"" + ID + "\"}", new String(message.getPayload()));
    }
}
