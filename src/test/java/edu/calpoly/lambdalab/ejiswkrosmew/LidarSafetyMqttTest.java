package edu.calpoly.lambdalab.ejiswkrosmew;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LidarSafetyMqttTest {
    private static final LidarSafetyMqttSubscriber subscriber = new LidarSafetyMqttSubscriber();
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    void validMessage() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "13d85b49-e7da-4638-b830-c05a1653fc5d",
            "operation", LidarSafetyMqttSubscriber.OPERATION,
            "result", Map.of("status", "WARNING", "minDist", 0.2)
        ));
        JsonNode root = subscriber.handleMessage(message);

        assertEquals("13d85b49-e7da-4638-b830-c05a1653fc5d", root.path("id").asText());
        assertEquals(LidarSafetyMqttSubscriber.OPERATION, root.path("operation").asText());
        assertEquals("WARNING", root.path("result").path("status").asText());
        assertEquals(0.2, root.path("result").path("minDist").asDouble());
    }

    @Test
    void missingId() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "operation", LidarSafetyMqttSubscriber.OPERATION,
            "result", Map.of("status", "WARNING", "minDist", 0.2)
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void idNotString() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", 8675309,
            "operation", LidarSafetyMqttSubscriber.OPERATION,
            "result", Map.of("status", "WARNING", "minDist", 0.2)
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void invalidId() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "not a valid id lol",
            "operation", LidarSafetyMqttSubscriber.OPERATION,
            "result", Map.of("status", "WARNING", "minDist", 0.2)
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void missingOperation() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "13d85b49-e7da-4638-b830-c05a1653fc5d",
            "result", Map.of("status", "WARNING", "minDist", 0.2)
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void operationNotString() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "13d85b49-e7da-4638-b830-c05a1653fc5d",
            "operation", 8675309,
            "result", Map.of("status", "WARNING", "minDist", 0.2)
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void wrongOperation() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "13d85b49-e7da-4638-b830-c05a1653fc5d",
            "operation", "This is not the right operation",
            "result", Map.of("status", "WARNING", "minDist", 0.2)
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void missingResult() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "13d85b49-e7da-4638-b830-c05a1653fc5d",
            "operation", LidarSafetyMqttSubscriber.OPERATION
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void resultNotObj() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "13d85b49-e7da-4638-b830-c05a1653fc5d",
            "operation", LidarSafetyMqttSubscriber.OPERATION,
            "result", "This is not an object"
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void emptyResult() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "13d85b49-e7da-4638-b830-c05a1653fc5d",
            "operation", LidarSafetyMqttSubscriber.OPERATION,
            "result", Map.of()
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void missingStatus() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "13d85b49-e7da-4638-b830-c05a1653fc5d",
            "operation", LidarSafetyMqttSubscriber.OPERATION,
            "result", Map.of("minDist", 0.2)
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void statusNotString() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "13d85b49-e7da-4638-b830-c05a1653fc5d",
            "operation", LidarSafetyMqttSubscriber.OPERATION,
            "result", Map.of("status", 573, "minDist", 0.2)
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void invalidStatus() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "13d85b49-e7da-4638-b830-c05a1653fc5d",
            "operation", LidarSafetyMqttSubscriber.OPERATION,
            "result", Map.of("status", "Not WARNING", "minDist", 0.2)
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void missingMinDist() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "13d85b49-e7da-4638-b830-c05a1653fc5d",
            "operation", LidarSafetyMqttSubscriber.OPERATION,
            "result", Map.of("status", "WARNING")
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }

    @Test
    void minDistNotDouble() throws JsonProcessingException {
        String message = mapper.writeValueAsString(Map.of(
            "id", "13d85b49-e7da-4638-b830-c05a1653fc5d",
            "operation", LidarSafetyMqttSubscriber.OPERATION,
            "result", Map.of("status", "WARNING", "minDist", "idk")
        ));

        assertThrows(IllegalArgumentException.class, () -> subscriber.handleMessage(message));
    }
}
