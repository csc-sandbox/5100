package edu.calpoly.lambdalab.jjuangon03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GazeClusterReliabilityTest {
    private static final String BODY =
            "{\"points\":[{\"x\":0.5,\"y\":0.5}],\"epsilon\":0.1,\"minPoints\":1}";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void emitsStructuredJsonEvent() throws Exception {
        List<String> events = new ArrayList<>();
        GazeClusterStructuredLogger logger = new GazeClusterStructuredLogger(events::add);

        logger.log(null, "gaze_cluster.persisted", Map.of("id", "request-123", "clusterCount", 2));

        assertEquals(1, events.size());
        JsonNode event = mapper.readTree(events.get(0));
        assertEquals("gaze_cluster.persisted", event.path("event").asText());
        assertEquals("GazeClusterLambda", event.path("operation").asText());
        assertEquals("request-123", event.path("id").asText());
        assertEquals(2, event.path("clusterCount").asInt());
    }

    @Test
    void retriesIdempotentStorageAndRecovers() {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        GazeClusterLambda.Storage flakyStorage = new GazeClusterLambda.Storage() {
            public void save(String id, String json) {
                if (attempts.incrementAndGet() < 3) throw new IllegalStateException("temporary failure");
            }
            public String get(String id) { return null; }
        };
        GazeClusterLambda lambda = lambda(flakyStorage,
                new GazeClusterRetryPolicy(3, 10, delays::add));

        assertEquals(200, lambda.handleRequest(Map.of("body", BODY), null).get("statusCode"));
        assertEquals(3, attempts.get());
        assertEquals(List.of(10L, 20L), delays);
    }

    @Test
    void returns500AfterBoundedRetryExhaustion() {
        AtomicInteger attempts = new AtomicInteger();
        GazeClusterLambda.Storage failingStorage = new GazeClusterLambda.Storage() {
            public void save(String id, String json) {
                attempts.incrementAndGet();
                throw new IllegalStateException("persistent failure");
            }
            public String get(String id) { return null; }
        };
        GazeClusterLambda lambda = lambda(failingStorage,
                new GazeClusterRetryPolicy(3, 0, ignored -> { }));

        assertEquals(500, lambda.handleRequest(Map.of("body", BODY), null).get("statusCode"));
        assertEquals(3, attempts.get());
    }

    @Test
    void retryPolicyPropagatesTheDeterministicFailure() {
        AtomicInteger attempts = new AtomicInteger();
        GazeClusterRetryPolicy policy = new GazeClusterRetryPolicy(2, 0, ignored -> { });
        assertThrows(IllegalStateException.class, () -> policy.execute(() -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("always fails");
        }));
        assertEquals(2, attempts.get());
    }

    private GazeClusterLambda lambda(GazeClusterLambda.Storage storage, GazeClusterRetryPolicy retryPolicy) {
        return new GazeClusterLambda(storage, null,
                () -> UUID.fromString("c5f1b1b7-ccf5-4b98-80f9-f1123589a123"),
                (context, event, fields) -> { }, retryPolicy);
    }
}
