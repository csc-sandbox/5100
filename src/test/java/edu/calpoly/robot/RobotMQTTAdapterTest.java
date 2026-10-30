package edu.calpoly.robot;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Unit tests for RobotMQTTAdapter message handling.
 * Replaces the manual TestRobotMQTTAdapter program. Payloads are passed
 * directly to the adapter's handler, so no MQTT broker connection is needed.
 *
 * @author Paul Motter (PaulMotter)
 * @version 2.0.0 (10/21/2026)
 */
class RobotMQTTAdapterTest {

    private static final String VALID_MESSAGE =
        "{\"jointAngles\":[2.0,1.75,1.0,0.5,0.25,0.0],"
        + "\"position\":[0.5,-10.0,5.25]}";

    private RobotBlackBoard blackboard;
    private AtomicReference<RobotMessage> lastCallback;
    private RobotMQTTAdapter adapter;

    @BeforeEach
    void setUp() {
        blackboard = new RobotBlackBoard();
        lastCallback = new AtomicReference<>();
        blackboard.callbackOnPost(lastCallback::set);
        // The constructor does not connect; only run() does.
        adapter = new RobotMQTTAdapter(
            RobotServer.MQTT_HOST, RobotServer.MQTT_PORT, RobotServer.ROBOT_ID, blackboard);
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void subscribesToContractTopic() {
        assertEquals("csc5100/robot/1", adapter.getTopic());
    }

    @Test
    void validPayloadIsPostedToBlackboard() {
        assertTrue(adapter.handlePayload(bytes(VALID_MESSAGE)));

        RobotMessage stored = blackboard.read();
        assertNotNull(stored);
        assertArrayEquals(new float[] {2.0f, 1.75f, 1.0f, 0.5f, 0.25f, 0.0f}, stored.jointAngles);
        assertArrayEquals(new float[] {0.5f, -10.0f, 5.25f}, stored.position);
    }

    @Test
    void validPayloadTriggersBlackboardCallback() {
        adapter.handlePayload(bytes(VALID_MESSAGE));

        RobotMessage notified = lastCallback.get();
        assertNotNull(notified, "callback (e.g. RobotGUI::update) should run on each valid message");
        assertArrayEquals(new float[] {0.5f, -10.0f, 5.25f}, notified.position);
    }

    @Test
    void wrongArrayLengthsAreRejected() {
        String payload = "{\"jointAngles\":[1.0,2.0],\"position\":[0.0]}";

        assertFalse(adapter.handlePayload(bytes(payload)));
        assertFalse(blackboard.hasData());
        assertNull(lastCallback.get());
    }

    @Test
    void missingFieldIsRejected() {
        String payload = "{\"jointAngles\":[1.0,2.0,3.0,4.0,5.0,6.0]}";

        assertFalse(adapter.handlePayload(bytes(payload)));
        assertFalse(blackboard.hasData());
    }

    @Test
    void malformedJsonIsRejected() {
        assertFalse(adapter.handlePayload(bytes("{\"jointAngles\":[1.0,")));
        assertFalse(blackboard.hasData());
    }

    @Test
    void legacyCsvFormatIsRejected() {
        assertFalse(adapter.handlePayload(bytes("ROBOT,1,2,3,4,5,6,7,8,9")));
        assertFalse(blackboard.hasData());
    }

    @Test
    void emptyPayloadIsRejected() {
        assertFalse(adapter.handlePayload(new byte[0]));
        assertFalse(blackboard.hasData());
    }

    @Test
    void invalidPayloadDoesNotOverwritePreviousData() {
        adapter.handlePayload(bytes(VALID_MESSAGE));
        adapter.handlePayload(bytes("not json"));

        RobotMessage stored = blackboard.read();
        assertNotNull(stored);
        assertArrayEquals(new float[] {0.5f, -10.0f, 5.25f}, stored.position);
    }

    @Test
    void latestValidPayloadWins() {
        adapter.handlePayload(bytes(VALID_MESSAGE));
        adapter.handlePayload(bytes(
            "{\"jointAngles\":[0.0,0.0,0.0,0.0,0.0,1.0],\"position\":[1.0,2.0,3.0]}"));

        assertArrayEquals(new float[] {1.0f, 2.0f, 3.0f}, blackboard.read().position);
    }

    @Test
    void acceptsJsonProducedByProviderSerialization() throws Exception {
        // RobotMQTTProvider.publish(RobotMessage) serializes with a plain ObjectMapper;
        // the adapter must accept that same representation.
        RobotMessage original = new RobotMessage(
            new float[] {0.1f, 0.2f, 0.3f, 0.4f, 0.5f, 0.6f},
            new float[] {-1.0f, 0.0f, 1.0f});
        byte[] payload = new ObjectMapper().writeValueAsBytes(original);

        assertTrue(adapter.handlePayload(payload));
        assertArrayEquals(original.jointAngles, blackboard.read().jointAngles);
        assertArrayEquals(original.position, blackboard.read().position);
    }
}
