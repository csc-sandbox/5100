package edu.calpoly.lambdalab.aidenrodriguez;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class EncryptMessageReliabilityTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Object> POST = Map.of("httpMethod", "POST", "body",
            "{\"message\":\"TOP-SECRET customer text\",\"shift\":\"1\"}");

    @Test
    void structuredLogHasRequiredFieldsAndDoesNotIncludePayloadOrSecrets() throws Exception {
        List<String> output = new ArrayList<>();
        long start = System.nanoTime();

        EncryptMessageObservability.log(output::add, "aws-request-123", "DynamoDB",
                "SAVE_RESULT", "SUCCESS", start, null);

        assertEquals(1, output.size());
        String json = output.get(0);
        JsonNode event = MAPPER.readTree(json);
        assertEquals("CSC5100", event.path("course").asText());
        assertEquals("EncryptMessageLambda", event.path("component").asText());
        assertEquals("DynamoDB", event.path("service").asText());
        assertEquals("SAVE_RESULT", event.path("event").asText());
        assertEquals("SUCCESS", event.path("status").asText());
        assertEquals("aws-request-123", event.path("requestId").asText());
        assertTrue(event.path("durationMs").isNumber());
        assertTrue(event.path("durationMs").asDouble() >= 0);
        assertFalse(json.contains("TOP-SECRET"));
        assertFalse(json.contains("customer text"));
        assertFalse(json.contains("password"));
    }

    @Test
    void injectedStorageFailureReturnsErrorAfterStorageAttempt() {
        int[] saves = {0};
        List<String> output = new ArrayList<>();
        Storage storage = new Storage() {
            @Override
            public void save(String id, String json) {
                saves[0]++;
            }

            @Override
            public String get(String id) {
                return null;
            }
        };
        Messaging failedSns = json -> {
            throw new IllegalStateException("SNS unavailable");
        };
        EncryptMessageLambda lambda = new EncryptMessageLambda(storage, failedSns, null,
                noRetryExecutor(), output::add);

        Map<String, Object> response = lambda.handleRequest(POST, null);

        assertEquals(502, response.get("statusCode"));
        assertEquals(1, saves[0]);
        JsonNode logEvent = loggedEvent(output, "SNS", "PUBLISH_EVENT");
        assertEquals("FAILURE", logEvent.path("status").asText());
        assertEquals("IllegalStateException", logEvent.path("errorType").asText());
    }

    @Test
    void retriesTransientStorageFailureUntilThirdAttemptSucceeds() {
        int[] attempts = {0};
        Storage storage = new Storage() {
            @Override
            public void save(String id, String json) {
                attempts[0]++;
                if (attempts[0] < 3) throw new TransientStorageFailure();
            }

            @Override
            public String get(String id) {
                return null;
            }
        };
        EncryptMessageLambda lambda = new EncryptMessageLambda(storage, null, null,
                retryTransientOnly(), ignored -> { });

        Map<String, Object> response = lambda.handleRequest(POST, null);

        assertEquals(200, response.get("statusCode"));
        assertEquals(3, attempts[0]);
    }

    @Test
    void stopsAfterThreeRetryableStorageFailures() {
        int[] attempts = {0};
        Storage storage = new Storage() {
            @Override
            public void save(String id, String json) {
                attempts[0]++;
                throw new TransientStorageFailure();
            }

            @Override
            public String get(String id) {
                return null;
            }
        };
        EncryptMessageLambda lambda = new EncryptMessageLambda(storage, null, null,
                retryTransientOnly(), ignored -> { });

        Map<String, Object> response = lambda.handleRequest(POST, null);

        assertEquals(500, response.get("statusCode"));
        assertEquals(3, attempts[0]);
        assertTrue(response.get("body").toString().contains("Unable to persist result"));
    }

    private static EncryptMessageRetryExecutor noRetryExecutor() {
        return new EncryptMessageRetryExecutor(3, failure -> false, ignored -> { });
    }

    private static EncryptMessageRetryExecutor retryTransientOnly() {
        return new EncryptMessageRetryExecutor(3, TransientStorageFailure.class::isInstance,
                ignored -> { });
    }

    private static JsonNode loggedEvent(List<String> logs, String service, String eventName) {
        return logs.stream().map(log -> {
            try {
                return MAPPER.readTree(log);
            } catch (JsonProcessingException e) {
                throw new AssertionError(e);
            }
        }).filter(event -> service.equals(event.path("service").asText())
                && eventName.equals(event.path("event").asText()))
                .findFirst().orElseThrow();
    }

    private static final class TransientStorageFailure extends RuntimeException { }
}