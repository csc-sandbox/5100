package edu.calpoly.lambdalab.paulmotter;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Broker-independent logic for the desktop client: parse an event, display it,
 * and publish an acknowledgment. Invalid events are displayed as rejected and
 * are not acknowledged.
 *
 * @author Paul Motter (PaulMotter)
 * @version 1.0 (10/21/2026)
 */
final class RobotDistanceEventHandler {
    static final String EVENT_TOPIC = "csc5100/events/robot-distance";
    static final String STATUS_TOPIC = "csc5100/status/";
    private static final int MAX_LOGGED_PAYLOAD = 200;

    private final ObjectMapper mapper = new ObjectMapper();
    private final BiConsumer<String, String> publisher;
    private final Consumer<String> display;
    private final String clientId;
    private final Clock clock;

    /**
     * @param publisher sends (topic, json); the MQTT client supplies a QoS 1 publisher
     * @param display receives human-readable output lines
     * @param clientId identifies this client in acknowledgments
     * @param clock source of the ACK timestamp
     */
    RobotDistanceEventHandler(
            BiConsumer<String, String> publisher,
            Consumer<String> display,
            String clientId,
            Clock clock) {
        this.publisher = Objects.requireNonNull(publisher);
        this.display = Objects.requireNonNull(display);
        this.clientId = Objects.requireNonNull(clientId);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Handles one MQTT payload.
     *
     * @param payload the raw message text
     * @return true if the event was valid and the ACK was published
     */
    boolean handle(String payload) {
        RobotDistanceEvent event;
        try {
            event = RobotDistanceEvent.parse(payload);
        } catch (IllegalArgumentException e) {
            display.accept("[rejected] " + e.getMessage() + ": " + truncate(payload));
            return false;
        }

        display.accept(format(event));

        try {
            String ack = ackJson(event);
            publisher.accept(STATUS_TOPIC+event.id(), ack);
            display.accept("[ack] " + STATUS_TOPIC + event.id() + " " + ack);
            return true;
        } catch (RuntimeException e) {
            display.accept("[ack failed] id=" + event.id() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Formats an event for the console.
     *
     * @param event a validated event
     * @return display text
     */
    String format(RobotDistanceEvent event) {
        String json;
        try {
            json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(event.raw());
        } catch (JsonProcessingException e) {
            json = event.raw().toString();
        }
        return "[event] " + EVENT_TOPIC + System.lineSeparator()
            + "  id:        " + event.id() + System.lineSeparator()
            + "  operation: " + event.operation() + System.lineSeparator()
            + "  distance:  " + event.result() + System.lineSeparator()
            + "  json:      " + json.replace("\n", "\n             ");
    }

    /**
     * Builds the acknowledgment JSON for an event.
     *
     * @param event a validated event
     * @return ACK JSON carrying the same correlation id
     */
    String ackJson(RobotDistanceEvent event) {
        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("id", event.id());
        ack.put("status", "ACK");
        ack.put("operation", event.operation());
        ack.put("result", event.result());
        ack.put("client", clientId);
        ack.put("receivedAt", clock.instant().toString());
        try {
            return mapper.writeValueAsString(ack);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize ACK", e);
        }
    }

    private static String truncate(String payload) {
        if (payload == null) return "null";
        return payload.length() <= MAX_LOGGED_PAYLOAD
            ? payload : payload.substring(0, MAX_LOGGED_PAYLOAD) + "...";
    }
}
