package edu.calpoly.lambdalab.hwrd22;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DataFreshnessReliabilityTest {
    private static final Map<String, Object> POST = Map.of("httpMethod", "POST", "body",
            "{\"current\":100,\"sample\":95,\"threshold\":10}");
    @Test
    void injectedStorageFailureReturnsErrorAfterStorageAttempt() {
        final boolean[] saveAttempted = {false};
        Storage storage = new Storage() {
            @Override
            public void save(String id, String json) {
                saveAttempted[0] = true;
            }

            @Override
            public String get(String id) {
                return null;
            }
        };

        Messaging failedSns = json -> {
            throw new IllegalStateException("SNS unavailable");
        };

        DataFreshnessLambda lambda = new DataFreshnessLambda(storage, failedSns);

        Map<String, Object> response = lambda.handleRequest(POST, null);

        assertEquals(500, response.get("statusCode"));
        assertTrue(saveAttempted[0]);  // Indicates that the storage attempt went through
    }
}
