package edu.calpoly.lambdalab.paulmotter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RobotDistanceLambda, grouped by endpoint. Running this class runs all groups.
 *
 * @author Paul Motter (PaulMotter)
 * @version 1.1
 */
class RobotDistanceLambdaTest {

    /**
     * Unit tests for the RobotDistanceLamnbda class.
     *
     * @author Paul Motter (PaulMotter)
     * @version 1.0
     */
    @Nested
    class LegacyGETTest {

        static private Map<String, Object>MakeEvent(
            double x1, double y1, double z1, double x2, double y2, double z2)
            {
                Map<String, Object> queryParams =
                    Map.of(
                        "x1", Double.toString(x1), "y1", Double.toString(y1), "z1", Double.toString(z1),
                    "x2", Double.toString(x2), "y2", Double.toString(y2), "z2", Double.toString(z2)
                    );

                return Map.of("queryStringParameters", queryParams);
            }

        /**
         * Tests a known and valid distance.
         */
        @Test
        void calculateDistance(){
            // Setup
            RobotDistanceLambda lambda = new RobotDistanceLambda();
            Map<String, Object> event = MakeEvent(2, -2, 5, 4, -5, -1); 
            // Action
            Map<String, Object> response = lambda.handleRequest(event, null);
            // Check
            assertEquals(200, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("\"distance\":7.0"));
        }

        /**
         * Tests a distance of zero.
         */
        @Test 
        void calculateZeroDistance(){
            RobotDistanceLambda lambda = new RobotDistanceLambda();
            Map<String, Object> event = MakeEvent(2, 2, 2, 2, 2, 2); 
            // Action
            Map<String, Object> response = lambda.handleRequest(event, null);
            // Check
            assertEquals(200, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("\"distance\":0.0"));
        }

        /**
         * Tests catching an invalid input with one vector only having 2 dimensions.
         */
        @Test 
        void invalidInputTooFewDimensions(){
            // Setup
            RobotDistanceLambda lambda = new RobotDistanceLambda();
            Map<String, Object> queryParams = Map.of(
                "x1", "2", "y1", "3",
                "x2", "-1", "y2", "5", "z2", "8" 
            );
            Map<String, Object> event = Map.of("queryStringParameters", queryParams);
            // Action
            Map<String, Object> response = lambda.handleRequest(event, null);
            // Check
            assertEquals(400, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("{\"error\":\"Invalid request\"}"));
        }

        /**
         * Tets the parsing of query params to fail correctly.
         */
        @Test 
        void invalidInputNoQueryParams(){
            // Setup
            RobotDistanceLambda lambda = new RobotDistanceLambda();
            Map<String, Object> event = new HashMap<String, Object>();
            // Action
            Map<String, Object> response = lambda.handleRequest(event, null);
            // Check
            assertEquals(400, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("{\"error\":\"Invalid request\"}"));
        }

        /**
         * Test that NaN is caught since Float.parseFloat("NaN") doesn't error.
         */
        @Test 
        void invalidInputNaNPosition(){
            // Setup
            RobotDistanceLambda lambda = new RobotDistanceLambda();
            Map<String, Object> queryParams = Map.of(
                "x1", "2", "y1", "3", "z1", "NaN",
                "x2", "-1", "y2", "5", "z2", "8" 
            );
            Map<String, Object> event = Map.of("queryStringParameters", queryParams);
            // Action
            Map<String, Object> response = lambda.handleRequest(event, null);
            // Check
            assertEquals(400, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("{\"error\":\"Invalid request\"}"));
        }

        /**
         * Test that Infinity is caught since Float.parseFloat("Infinity") doesn't error.
         */
        @Test 
        void invalidInputInfinitePosition(){
            // Setup
            RobotDistanceLambda lambda = new RobotDistanceLambda();
            Map<String, Object> queryParams = Map.of(
                "x1", "2", "y1", "3", "z1", "Infinity",
                "x2", "-1", "y2", "5", "z2", "8" 
            );
            Map<String, Object> event = Map.of("queryStringParameters", queryParams);
            // Action
            Map<String, Object> response = lambda.handleRequest(event, null);
            // Check
            assertEquals(400, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("{\"error\":\"Invalid request\"}"));
        }

    }

    /**
     * In-memory fakes shared by the GET and POST tests, so no AWS service is used.
     */
    static final class RobotDistanceFakes {
        private RobotDistanceFakes() { }

        /** Records saves and serves them back by id, like DynamoDB keyed on "id". */
        static class FakeStorage implements RobotDistanceLambda.StorageAdapter {
            final Map<String, String> items = new HashMap<>();
            final List<RobotDistanceLambdaData> saved = new ArrayList<>();

            @Override
            public void save(RobotDistanceLambdaData data, String json) {
                saved.add(data);
                items.put(data.id, json);
            }

            @Override
            public String get(String id) {
                return items.get(id);
            }
        }

        /** In-memory DynamoDB client: stores putItem items by "id" and serves them from getItem. */
        static class FakeDynamo implements DynamoDbClient {
            final Map<String, Map<String, AttributeValue>> items = new HashMap<>();

            @Override
            public PutItemResponse putItem(PutItemRequest request) {
                items.put(request.item().get("id").s(), request.item());
                return PutItemResponse.builder().build();
            }

            @Override
            public GetItemResponse getItem(GetItemRequest request) {
                Map<String, AttributeValue> item = items.get(request.key().get("id").s());
                return item == null
                    ? GetItemResponse.builder().build()
                    : GetItemResponse.builder().item(item).build();
            }

            @Override
            public String serviceName() { return "dynamodb"; }

            @Override
            public void close() { }
        }

        /** In-memory S3 client: records the text written to each object key. */
        static class FakeS3 implements S3Client {
            final Map<String, String> objects = new HashMap<>();

            @Override
            public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
                try (InputStream in = body.contentStreamProvider().newStream()) {
                    objects.put(request.key(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return PutObjectResponse.builder().build();
            }

            @Override
            public String serviceName() { return "s3"; }

            @Override
            public void close() { }
        }

        /** Body map with string values, as RobotDistanceLambdaData.fromParams expects. */
        static Map<String, Object> body(String x1, String y1, String z1, String x2, String y2, String z2) {
            return Map.of("x1", x1, "y1", y1, "z1", z1, "x2", x2, "y2", y2, "z2", z2);
        }

        static Map<String, Object> post(Map<String, Object> body) {
            return Map.of("httpMethod", "POST", "body", body);
        }

        static Map<String, Object> getById(String id) {
            return Map.of("httpMethod", "GET", "queryStringParameters", Map.of("id", id));
        }
    }

    /**
     * Unit tests for GET /robot-distance?id=... (lookup of a stored result).
     *
     * @author Paul Motter (PaulMotter)
     * @version 1.0
     */
    @Nested
    class GETTest {
        private static final String ID = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";

        private final ObjectMapper mapper = new ObjectMapper();
        private RobotDistanceFakes.FakeStorage storage;
        private List<String> published;
        private RobotDistanceLambda lambda;

        @BeforeEach
        void setUp() {
            storage = new RobotDistanceFakes.FakeStorage();
            published = new ArrayList<>();
            lambda = new RobotDistanceLambda(storage, published::add);
        }

        /**
         * Tests that a stored result is returned unchanged.
         */
        @Test
        void returnsStoredResultForKnownId() {
            String json = "{\"id\":\"" + ID + "\",\"distance\":7.0}";
            storage.items.put(ID, json);

            Map<String, Object> response = lambda.handleRequest(RobotDistanceFakes.getById(ID), null);

            assertEquals(200, response.get("statusCode"));
            assertEquals(json, response.get("body"));
        }

        /**
         * Tests that a POST result can be looked up by the id it returned.
         */
        @Test
        void postThenGetReturnsSameResult() throws Exception {
            Map<String, Object> posted = lambda.handleRequest(
                RobotDistanceFakes.post(RobotDistanceFakes.body("2", "-2", "5", "4", "-5", "-1")), null);
            String id = mapper.readTree(posted.get("body").toString()).get("id").asText();

            Map<String, Object> fetched = lambda.handleRequest(RobotDistanceFakes.getById(id), null);

            assertEquals(200, fetched.get("statusCode"));
            JsonNode body = mapper.readTree(fetched.get("body").toString());
            assertEquals(id, body.get("id").asText());
            assertEquals(7.0, body.get("distance").asDouble(), 1e-6);
            assertEquals(2.0, body.get("x1").asDouble(), 1e-6);
        }

        /**
         * Tests a well-formed id with no stored item.
         */
        @Test
        void unknownIdReturns404() {
            Map<String, Object> response = lambda.handleRequest(RobotDistanceFakes.getById(ID), null);

            assertEquals(404, response.get("statusCode"));
            assertTrue(response.get("body").toString().contains("Not found"));
        }

        /**
         * Tests that a malformed id is rejected before storage is queried.
         */
        @Test
        void invalidIdReturns400() {
            Map<String, Object> response = lambda.handleRequest(RobotDistanceFakes.getById("not-a-uuid"), null);

            assertEquals(400, response.get("statusCode"));
        }

        /**
         * Tests that a non-canonical UUID (uppercase) is rejected.
         */
        @Test
        void uppercaseIdReturns400() {
            Map<String, Object> response = lambda.handleRequest(
                RobotDistanceFakes.getById(ID.toUpperCase()), null);

            assertEquals(400, response.get("statusCode"));
        }

        /**
         * Tests lookup when the storage environment variables are not set.
         */
        @Test
        void missingStorageReturns503() {
            RobotDistanceLambda unconfigured = new RobotDistanceLambda(null, published::add);

            Map<String, Object> response = unconfigured.handleRequest(RobotDistanceFakes.getById(ID), null);

            assertEquals(503, response.get("statusCode"));
        }

        /**
         * Tests that a storage failure becomes a generic 500.
         */
        @Test
        void storageFailureReturns500() {
            RobotDistanceLambda failing = new RobotDistanceLambda(new RobotDistanceLambda.StorageAdapter() {
                public void save(RobotDistanceLambdaData data, String json) { }
                public String get(String id) { throw new IllegalStateException("DynamoDB unavailable"); }
            }, published::add);

            Map<String, Object> response = failing.handleRequest(RobotDistanceFakes.getById(ID), null);

            assertEquals(500, response.get("statusCode"));
            assertFalse(response.get("body").toString().contains("DynamoDB"),
                "AWS details must not leak to the caller");
        }

        /**
         * Tests that a lookup is read-only.
         */
        @Test
        void getDoesNotSaveOrPublish() {
            storage.items.put(ID, "{}");

            lambda.handleRequest(RobotDistanceFakes.getById(ID), null);

            assertTrue(storage.saved.isEmpty());
            assertTrue(published.isEmpty());
        }
    }

    /**
     * Tests the real RobotDistanceStorageAdapter against in-memory DynamoDB and S3 clients,
     * so the attributes it writes are the ones it reads back.
     *
     * @author Paul Motter (PaulMotter)
     * @version 1.0
     */
    @Nested
    class StorageAdapterTest {
        private final ObjectMapper mapper = new ObjectMapper();
        private RobotDistanceFakes.FakeDynamo dynamo;
        private RobotDistanceFakes.FakeS3 s3;
        private RobotDistanceLambda lambda;

        @BeforeEach
        void setUp() {
            dynamo = new RobotDistanceFakes.FakeDynamo();
            s3 = new RobotDistanceFakes.FakeS3();
            RobotDistanceStorageAdapter adapter =
                new RobotDistanceStorageAdapter("table", "bucket", s3, dynamo);
            lambda = new RobotDistanceLambda(adapter, json -> { });
        }

        /**
         * Tests that a POST result can be retrieved by its UUID, and that DynamoDB
         * stores the exact JSON that was written to S3.
         */
        @Test
        void postResultCanBeRetrievedByUuid() throws Exception {
            Map<String, Object> posted = lambda.handleRequest(
                RobotDistanceFakes.post(RobotDistanceFakes.body("2", "-2", "5", "4", "-5", "-1")), null);
            assertEquals(200, posted.get("statusCode"));
            String postBody = posted.get("body").toString();
            String id = mapper.readTree(postBody).get("id").asText();

            Map<String, Object> fetched = lambda.handleRequest(RobotDistanceFakes.getById(id), null);

            assertEquals(200, fetched.get("statusCode"));
            assertEquals(postBody, fetched.get("body"));
            assertEquals(postBody, dynamo.items.get(id).get("resultJson").s());
            assertEquals(postBody, s3.objects.get("PaulMotter/DistanceLambda/" + id + ".json"));
        }
    }

    /**
     * Unit tests for POST /robot-distance (calculate, store, and publish).
     *
     * @author Paul Motter (PaulMotter)
     * @version 1.0
     */
    @Nested
    class POSTTest {
        private final ObjectMapper mapper = new ObjectMapper();
        private RobotDistanceFakes.FakeStorage storage;
        private List<String> published;
        private RobotDistanceLambda lambda;

        @BeforeEach
        void setUp() {
            storage = new RobotDistanceFakes.FakeStorage();
            published = new ArrayList<>();
            lambda = new RobotDistanceLambda(storage, published::add);
        }

        private Map<String, Object> postValid() {
            return lambda.handleRequest(
                RobotDistanceFakes.post(RobotDistanceFakes.body("2", "-2", "5", "4", "-5", "-1")), null);
        }

        /**
         * Tests a known distance and that the response carries a generated UUID.
         */
        @Test
        void validPostReturnsDistanceAndUuid() throws Exception {
            Map<String, Object> response = postValid();

            assertEquals(200, response.get("statusCode"));
            JsonNode body = mapper.readTree(response.get("body").toString());
            assertEquals(7.0, body.get("distance").asDouble(), 1e-6);
            String id = body.get("id").asText();
            assertEquals(id, UUID.fromString(id).toString());
        }

        /**
         * Tests that exactly one item is stored, keyed by the returned id.
         */
        @Test
        void validPostSavesOneItemWithSameId() throws Exception {
            Map<String, Object> response = postValid();
            String id = mapper.readTree(response.get("body").toString()).get("id").asText();

            assertEquals(1, storage.saved.size());
            assertEquals(id, storage.saved.get(0).id);
            assertEquals(7.0f, storage.saved.get(0).distance, 1e-6f);
            assertEquals(response.get("body"), storage.items.get(id),
                "stored JSON should match the response");
        }

        /**
         * Tests that one event is published with the fields EventProcessorLambda routes on.
         */
        @Test
        void validPostPublishesCorrelatedEvent() throws Exception {
            Map<String, Object> response = postValid();
            String id = mapper.readTree(response.get("body").toString()).get("id").asText();

            assertEquals(1, published.size());
            JsonNode event = mapper.readTree(published.get(0));
            assertEquals(id, event.get("id").asText());
            assertEquals("RobotDistanceLambda", event.get("operation").asText());
            assertEquals(7.0, event.get("result").asDouble(), 1e-6);
        }

        /**
         * Tests that the published event can be read by the desktop client.
         */
        @Test
        void publishedEventIsAcceptedByDesktopClientParser() {
            postValid();

            RobotDistanceEvent event = RobotDistanceEvent.parse(published.get(0));
            assertEquals(7.0, event.result(), 1e-6);
        }

        /**
         * Tests that every POST gets its own id.
         */
        @Test
        void eachPostGetsUniqueId() throws Exception {
            String first = mapper.readTree(postValid().get("body").toString()).get("id").asText();
            String second = mapper.readTree(postValid().get("body").toString()).get("id").asText();

            assertNotEquals(first, second);
            assertEquals(2, storage.items.size());
        }

        /**
         * Tests a zero distance boundary.
         */
        @Test
        void identicalPointsGiveZeroDistance() throws Exception {
            Map<String, Object> response = lambda.handleRequest(
                RobotDistanceFakes.post(RobotDistanceFakes.body("1", "1", "1", "1", "1", "1")), null);

            assertEquals(200, response.get("statusCode"));
            assertEquals(0.0, mapper.readTree(response.get("body").toString()).get("distance").asDouble());
        }

        /**
         * Tests that a missing coordinate is rejected without side effects.
         */
        @Test
        void missingCoordinateReturns400WithoutSideEffects() {
            Map<String, Object> body = new HashMap<>(RobotDistanceFakes.body("2", "-2", "5", "4", "-5", "-1"));
            body.remove("z2");

            Map<String, Object> response = lambda.handleRequest(RobotDistanceFakes.post(body), null);

            assertEquals(400, response.get("statusCode"));
            assertTrue(storage.saved.isEmpty());
            assertTrue(published.isEmpty());
        }

        /**
         * Tests a non-numeric coordinate.
         */
        @Test
        void nonNumericCoordinateReturns400() {
            Map<String, Object> response = lambda.handleRequest(
                RobotDistanceFakes.post(RobotDistanceFakes.body("abc", "-2", "5", "4", "-5", "-1")), null);

            assertEquals(400, response.get("statusCode"));
            assertTrue(storage.saved.isEmpty());
        }

        /**
         * Tests that NaN and Infinity are rejected (Float.parseFloat accepts both).
         */
        @Test
        void nonFiniteCoordinatesReturn400() {
            Map<String, Object> nan = lambda.handleRequest(
                RobotDistanceFakes.post(RobotDistanceFakes.body("NaN", "0", "0", "0", "0", "0")), null);
            Map<String, Object> inf = lambda.handleRequest(
                RobotDistanceFakes.post(RobotDistanceFakes.body("Infinity", "0", "0", "0", "0", "0")), null);

            assertEquals(400, nan.get("statusCode"));
            assertEquals(400, inf.get("statusCode"));
            assertTrue(storage.saved.isEmpty());
            assertTrue(published.isEmpty());
        }

        /**
         * Tests a POST with no body.
         */
        @Test
        void missingBodyReturns400() {
            Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "POST"), null);

            assertEquals(400, response.get("statusCode"));
        }

        /**
         * Tests the HTTP API (payload v2) method location: requestContext.http.method.
         */
        @Test
        void acceptsMethodFromRequestContext() {
            Map<String, Object> event = Map.of(
                "requestContext", Map.of("http", Map.of("method", "POST")),
                "body", RobotDistanceFakes.body("0", "0", "0", "3", "4", "0"));

            Map<String, Object> response = lambda.handleRequest(event, null);

            assertEquals(200, response.get("statusCode"));
            assertEquals(1, storage.saved.size());
        }

        /**
         * Tests that nothing is published when storage is not configured.
         */
        @Test
        void missingStorageReturns503AndDoesNotPublish() {
            RobotDistanceLambda unconfigured = new RobotDistanceLambda(null, published::add);

            Map<String, Object> response = unconfigured.handleRequest(
                RobotDistanceFakes.post(RobotDistanceFakes.body("2", "-2", "5", "4", "-5", "-1")), null);

            assertEquals(503, response.get("statusCode"));
            assertTrue(published.isEmpty());
        }

        /**
         * Tests the response when messaging is not configured.
         */
        @Test
        void missingMessagingReturns503() {
            RobotDistanceLambda unconfigured = new RobotDistanceLambda(storage, null);

            Map<String, Object> response = unconfigured.handleRequest(
                RobotDistanceFakes.post(RobotDistanceFakes.body("2", "-2", "5", "4", "-5", "-1")), null);

            assertEquals(503, response.get("statusCode"));
        }

        /**
         * Tests that a storage failure becomes a generic 500 and nothing is published.
         */
        @Test
        void storageFailureReturns500AndDoesNotPublish() {
            RobotDistanceLambda failing = new RobotDistanceLambda(new RobotDistanceLambda.StorageAdapter() {
                public void save(RobotDistanceLambdaData data, String json) { throw new IllegalStateException("S3 down"); }
                public String get(String id) { return null; }
            }, published::add);

            Map<String, Object> response = failing.handleRequest(
                RobotDistanceFakes.post(RobotDistanceFakes.body("2", "-2", "5", "4", "-5", "-1")), null);

            assertEquals(500, response.get("statusCode"));
            assertTrue(published.isEmpty());
        }

        /**
         * Tests that a messaging failure becomes a generic 500.
         */
        @Test
        void messagingFailureReturns500() {
            RobotDistanceLambda failing = new RobotDistanceLambda(storage,
                json -> { throw new IllegalStateException("SNS down"); });

            Map<String, Object> response = failing.handleRequest(
                RobotDistanceFakes.post(RobotDistanceFakes.body("2", "-2", "5", "4", "-5", "-1")), null);

            assertEquals(500, response.get("statusCode"));
        }

        /**
         * Tests that unsupported methods are rejected.
         */
        @Test
        void unsupportedMethodReturns405() {
            Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "DELETE"), null);

            assertEquals(405, response.get("statusCode"));
            assertTrue(storage.saved.isEmpty());
        }

        /**
         * Tests the API Gateway shape: body is a JSON string with numeric values.
         */
        @Test
        void jsonStringBodyWithNumbersIsAccepted() throws Exception {
            Map<String, Object> event = Map.of("httpMethod", "POST",
                "body", "{\"x1\":2,\"y1\":-2,\"z1\":5,\"x2\":4.0,\"y2\":-5.0,\"z2\":-1}");

            Map<String, Object> response = lambda.handleRequest(event, null);

            assertEquals(200, response.get("statusCode"));
            assertEquals(7.0, mapper.readTree(response.get("body").toString()).get("distance").asDouble(), 1e-6);
            assertEquals(1, storage.saved.size());
            assertEquals(1, published.size());
        }

        /**
         * Tests a JSON string body whose values are quoted numbers.
         */
        @Test
        void jsonStringBodyWithStringValuesIsAccepted() throws Exception {
            Map<String, Object> event = Map.of("httpMethod", "POST",
                "body", "{\"x1\":\"0\",\"y1\":\"0\",\"z1\":\"0\",\"x2\":\"3\",\"y2\":\"4\",\"z2\":\"0\"}");

            Map<String, Object> response = lambda.handleRequest(event, null);

            assertEquals(200, response.get("statusCode"));
            assertEquals(5.0, mapper.readTree(response.get("body").toString()).get("distance").asDouble(), 1e-6);
        }

        /**
         * Tests that malformed or non-object JSON bodies are a client error, not a 500.
         */
        @Test
        void malformedJsonBodyReturns400() {
            for (String bad : new String[] {"{\"x1\":", "not json", "[1,2,3]"}) {
                Map<String, Object> response = lambda.handleRequest(
                    Map.of("httpMethod", "POST", "body", bad), null);
                assertEquals(400, response.get("statusCode"), "body: " + bad);
            }
            assertTrue(storage.saved.isEmpty());
            assertTrue(published.isEmpty());
        }

        /**
         * Tests that a JSON null coordinate is rejected.
         */
        @Test
        void nullCoordinateInJsonBodyReturns400() {
            Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "POST",
                "body", "{\"x1\":null,\"y1\":0,\"z1\":0,\"x2\":3,\"y2\":4,\"z2\":0}"), null);

            assertEquals(400, response.get("statusCode"));
            assertTrue(storage.saved.isEmpty());
        }
    }
}
