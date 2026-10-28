package edu.calpoly.lambdalab.ejiswkrosmew;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LidarSafetyLambdaTest {
    @Test
    void warningDistance() {

        LidarSafetyLambda lambda = new LidarSafetyLambda();
        Map<String, Object> input = Map.of(
                "body", "{\"distances\": [1.7, 2.5, 0.2, 0.7], \"threshold\": 0.9}"
        );
        Map<String, Object> res = lambda.handleRequest(input, null);
        assertEquals(200, res.get("statusCode"));
        assertTrue(res.get("body").toString().contains("\"status\":\"WARNING\""));
        assertTrue(res.get("body").toString().contains("\"minDist\":0.2"));
    }

    @Test
    void safeDistance() {
        LidarSafetyLambda lambda = new LidarSafetyLambda();
        Map<String, Object> input = Map.of(
                "body", "{\"distances\": [1.5, 1.0, 2.2, 1.3], \"threshold\": 0.4}"
        );
        Map<String, Object> res = lambda.handleRequest(input, null);
        assertEquals(200, res.get("statusCode"));
        assertTrue(res.get("body").toString().contains("\"status\":\"SAFE\""));
        assertTrue(res.get("body").toString().contains("\"minDist\":1.0"));
    }

    @Test
    void emptyBody() {
        LidarSafetyLambda lambda = new LidarSafetyLambda();
        Map<String, Object> input = Map.of(
                "body", ""
        );
        Map<String, Object> res = lambda.handleRequest(input, null);
        assertEquals(400, res.get("statusCode"));
    }

    @Test
    void notJSON() {
        LidarSafetyLambda lambda = new LidarSafetyLambda();
        Map<String, Object> input = Map.of(
                "body", "foobar"
        );
        Map<String, Object> res = lambda.handleRequest(input, null);
        assertEquals(400, res.get("statusCode"));
    }

    @Test
    void missingDists() {
        LidarSafetyLambda lambda = new LidarSafetyLambda();
        Map<String, Object> input = Map.of(
                "body", "{\"threshold\": 0.4}"
        );
        Map<String, Object> res = lambda.handleRequest(input, null);
        assertEquals(400, res.get("statusCode"));
    }

    @Test
    void missingThreshold() {
        LidarSafetyLambda lambda = new LidarSafetyLambda();
        Map<String, Object> input = Map.of(
                "body", "{\"distances\": [1.5, 1.0, 2.2, 1.3]}"
        );
        Map<String, Object> res = lambda.handleRequest(input, null);
        assertEquals(400, res.get("statusCode"));
    }

    @Test
    void distsNotArray() {
        LidarSafetyLambda lambda = new LidarSafetyLambda();
        Map<String, Object> input = Map.of(
                "body", "{\"distances\": 1.5, \"threshold\": 0.4}"
        );
        Map<String, Object> res = lambda.handleRequest(input, null);
        assertEquals(400, res.get("statusCode"));
    }

    @Test
    void dists2DArray() {
        LidarSafetyLambda lambda = new LidarSafetyLambda();
        Map<String, Object> input = Map.of(
                "body", "{\"distances\": [[1.5, 1.0, 2.2, 1.3]], \"threshold\": 0.4}"
        );
        Map<String, Object> res = lambda.handleRequest(input, null);
        assertEquals(400, res.get("statusCode"));
    }

    @Test
    void distsNotAllDoubles() {
        LidarSafetyLambda lambda = new LidarSafetyLambda();
        Map<String, Object> input = Map.of(
                "body", "{\"distances\": [1.5, \"foo\", 2.2, 1.3], \"threshold\": 0.4}"
        );
        Map<String, Object> res = lambda.handleRequest(input, null);
        assertEquals(400, res.get("statusCode"));
    }

    @Test
    void thresholdNotDouble() {
        LidarSafetyLambda lambda = new LidarSafetyLambda();
        Map<String, Object> input = Map.of(
                "body", "{\"distances\": [1.5, 1.0, 2.2, 1.3], \"threshold\": \"bar\"}"
        );
        Map<String, Object> res = lambda.handleRequest(input, null);
        assertEquals(400, res.get("statusCode"));
    }

    @Test
    void postPersistsAndGetRetrievesSameId() {
        class FakeStorage implements LidarSafetyLambda.Storage {
            final Map<String, String> values = new HashMap<>();
            public void save(String id, String json) { values.put(id, json); }
            public String get(String id) { return values.get(id); }
        }
        FakeStorage storage = new FakeStorage();
        LidarSafetyLambda lambda = new LidarSafetyLambda(storage);
        Map<String, Object> post = Map.of(
            "httpMethod", "POST",
            "body", "{\"distances\": [1.7, 2.5, 0.2, 0.7], \"threshold\": 0.9}"
        );
        Map<String, Object> res = lambda.handleRequest(post, null);
        assertEquals(200, res.get("statusCode"));
        assertTrue(res.get("body").toString().contains("\"id\":"));
        String id = res.get("body").toString().replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        assertTrue(storage.values.containsKey(id));
        assertTrue(res.get("body").toString().contains("\"status\":\"WARNING\""));
        assertTrue(res.get("body").toString().contains("\"minDist\":0.2"));

        Map<String, Object> get = Map.of(
            "httpMethod", "GET",
            "queryStringParameters", Map.of("id", id)
        );
        Map<String, Object> fetched = lambda.handleRequest(get, null);
        assertEquals(200, fetched.get("statusCode"));
        assertTrue(fetched.get("body").toString().contains(id));
        assertTrue(fetched.get("body").toString().contains("\"status\":\"WARNING\""));
        assertTrue(fetched.get("body").toString().contains("\"minDist\":0.2"));
    }

    @Test
    void rejectsInvalidId() {
        LidarSafetyLambda lambda = new LidarSafetyLambda(new LidarSafetyLambda.Storage() {
            public void save(String id, String json ) { }
            public String get(String id) { return null; }
        });
        Map<String, Object> notUuid = Map.of(
            "httpMethod", "GET",
            "queryStringParameters", Map.of("id", "this is not a UUID lol")
        );
        assertEquals(400, lambda.handleRequest(notUuid, null).get("statusCode"));
    }

    @Test
    void rejectMissingRecord() {
        LidarSafetyLambda lambda = new LidarSafetyLambda(new LidarSafetyLambda.Storage() {
            public void save(String id, String json ) { }
            public String get(String id) { return null; }
        });

        Map<String, Object> invalidUuid = Map.of(
            "httpMethod", "GET",
            "queryStringParameters", Map.of("id", "46bf9dfc-2e21-402a-b099-28d6ed098dc6")
        );
        assertEquals(404, lambda.handleRequest(invalidUuid, null).get("statusCode"));
    }

    @Test
    void postSendsPayloadToMessagingChannels() {
        List<String> messages = new ArrayList<>();
        LidarSafetyLambda lambda = new LidarSafetyLambda(new LidarSafetyLambda.Storage() {
            public void save(String id, String json ) { }
            public String get(String id) { return null; }
        }, messages::add);

        String id = "e465fd07-1bc9-4bc2-ae07-7b0402921cd2";
        Map<String, Object> req = Map.of(
            "httpMethod", "POST",
            "body", "{\"id\":\"" + id + "\", \"distances\": [1.7, 2.5, 0.2, 0.7], \"threshold\": 0.9}"
        );
        Map<String, Object> res = lambda.handleRequest(req, null);

        assertEquals(200, res.get("statusCode"));
        assertEquals(1, messages.size());
        assertTrue(messages.get(0).contains(id));
        assertTrue(res.get("body").toString().contains("\"sns\":\"published\""));
        assertTrue(res.get("body").toString().contains("\"sqs\":\"queued\""));
    }

    @Test
    void legacyGetNoPublish() {
        List<String> messages = new ArrayList<>();
        LidarSafetyLambda lambda = new LidarSafetyLambda(null, messages::add);
        Map<String, Object> req = Map.of(
            "httpMethod", "GET",
            "queryStringParameters", Map.of(
                "distances", "[1.5, 1.0, 2.2, 1.3]",
                "threshold", "0.9"
            )
        );
        Map<String, Object> res = lambda.handleRequest(req, null);
        assertEquals(200, res.get("statusCode"));
        assertTrue(messages.isEmpty());
    }
}
