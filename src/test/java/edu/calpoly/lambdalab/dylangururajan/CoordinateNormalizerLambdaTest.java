package edu.calpoly.lambdalab.dylangururajan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// Unit tests
class CoordinateNormalizerLambdaTest {

  private final CoordinateNormalizerLambda lambda = new CoordinateNormalizerLambda();
  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void normalizesCenterPoint() throws JsonProcessingException {
    Map<String, Object> response = request("960", "540", "1920", "1080");

    assertEquals(200, response.get("statusCode"));
    assertEquals("application/json", headers(response).get("Content-Type"));
    JsonNode body = mapper.readTree(response.get("body").toString());
    assertEquals(0.5, body.get("normalizedX").asDouble(), 0.000001);
    assertEquals(0.5, body.get("normalizedY").asDouble(), 0.000001);
  }

  @Test
  void normalizesImageBoundary() throws JsonProcessingException {
    Map<String, Object> response = request("1920", "1080", "1920", "1080");

    assertEquals(200, response.get("statusCode"));
    JsonNode body = mapper.readTree(response.get("body").toString());
    assertEquals(1.0, body.get("normalizedX").asDouble(), 0.000001);
    assertEquals(1.0, body.get("normalizedY").asDouble(), 0.000001);
  }

  @Test
  void rejectsNonPositiveDimensions() {
    Map<String, Object> zeroWidth = request("0", "0", "0", "1080");
    Map<String, Object> negativeHeight = request("0", "0", "1920", "-1");

    assertEquals(400, zeroWidth.get("statusCode"));
    assertEquals(400, negativeHeight.get("statusCode"));
  }

  @Test
  void rejectsMissingAndInvalidInput() {
    Map<String, Object> missingCoordinate = request(null, "540", "1920", "1080");
    Map<String, Object> nonNumericCoordinate = request("left", "540", "1920", "1080");
    Map<String, Object> outsideImage = request("1921", "540", "1920", "1080");

    assertEquals(400, missingCoordinate.get("statusCode"));
    assertEquals(400, nonNumericCoordinate.get("statusCode"));
    assertEquals(400, outsideImage.get("statusCode"));
  }

  @Test
  void postUsesOneGeneratedUuidForResponseStorageAndMessaging() throws Exception {
    class FakeStorage implements CoordinateNormalizerLambda.Storage {
      String id;
      String json;

      @Override
      public void save(String savedId, String savedJson) {
        id = savedId;
        json = savedJson;
      }

      @Override
      public String get(String requestedId) {
        return requestedId.equals(id) ? json : null;
      }
    }

    FakeStorage storage = new FakeStorage();
    List<String> messages = new ArrayList<>();
    UUID expectedId = UUID.fromString("c5f1b1b7-ccf5-4b98-80f9-f1123589a123");
    CoordinateNormalizerLambda postLambda =
        new CoordinateNormalizerLambda(storage, messages::add, () -> expectedId);

    Map<String, Object> response = postLambda.handleRequest(
        Map.of("httpMethod", "POST", "body",
            "{\"x\":960,\"y\":540,\"width\":1920,\"height\":1080}"),
        null);

    assertEquals(200, response.get("statusCode"));
    assertEquals(expectedId.toString(), storage.id);
    assertEquals(response.get("body"), storage.json);
    assertEquals(List.of(storage.json), messages);

    JsonNode body = mapper.readTree(storage.json);
    assertEquals(expectedId.toString(), body.get("id").asText());
    assertEquals("CoordinateNormalizerLambda", body.get("operation").asText());
    assertEquals(0.5, body.path("result").path("normalizedX").asDouble(), 0.000001);
    assertEquals(0.5, body.path("result").path("normalizedY").asDouble(), 0.000001);
  }

  @Test
  void getByUuidReturnsPersistedJsonAndMissingReturns404() {
    String id = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
    String json = "{\"id\":\"" + id + "\",\"result\":{}}";
    CoordinateNormalizerLambda lookupLambda =
        new CoordinateNormalizerLambda(new CoordinateNormalizerLambda.Storage() {
          @Override
          public void save(String savedId, String savedJson) { }

          @Override
          public String get(String requestedId) {
            return id.equals(requestedId) ? json : null;
          }
        });

    Map<String, Object> found = lookupLambda.handleRequest(
        Map.of("httpMethod", "GET", "queryStringParameters", Map.of("id", id)), null);
    Map<String, Object> missing = lookupLambda.handleRequest(
        Map.of("httpMethod", "GET", "queryStringParameters", Map.of(
            "id", "14caa8ee-ca63-41f4-b57f-074007b2affc")), null);

    assertEquals(200, found.get("statusCode"));
    assertEquals(json, found.get("body"));
    assertEquals(404, missing.get("statusCode"));
  }

  @Test
  void supportsHttpApiV2MethodAndPathUuidLookup() {
    String id = "14caa8ee-ca63-41f4-b57f-074007b2affc";
    CoordinateNormalizerLambda lookupLambda =
        new CoordinateNormalizerLambda(new CoordinateNormalizerLambda.Storage() {
          @Override
          public void save(String savedId, String savedJson) { }

          @Override
          public String get(String requestedId) {
            return "{\"id\":\"" + requestedId + "\"}";
          }
        });

    Map<String, Object> response = lookupLambda.handleRequest(Map.of(
        "requestContext", Map.of("http", Map.of("method", "GET")),
        "pathParameters", Map.of("uuid", id)), null);

    assertEquals(200, response.get("statusCode"));
    assertTrue(response.get("body").toString().contains(id));
  }

  @Test
  void createsAUniqueUuidForEachPostOperation() throws Exception {
    List<String> messages = new ArrayList<>();
    CoordinateNormalizerLambda postLambda =
        new CoordinateNormalizerLambda(null, messages::add);
    Map<String, Object> post = Map.of("httpMethod", "POST", "body",
        "{\"x\":1,\"y\":1,\"width\":2,\"height\":2}");

    JsonNode first = mapper.readTree(postLambda.handleRequest(post, null).get("body").toString());
    JsonNode second = mapper.readTree(postLambda.handleRequest(post, null).get("body").toString());

    assertNotEquals(first.get("id").asText(), second.get("id").asText());
    assertEquals(2, messages.size());
  }

  @Test
  void rejectsInvalidLookupIdAndMalformedPostBody() {
    CoordinateNormalizerLambda configured =
        new CoordinateNormalizerLambda(new CoordinateNormalizerLambda.Storage() {
          public void save(String id, String json) { }
          public String get(String id) { return null; }
        });

    Map<String, Object> invalidId = configured.handleRequest(
        Map.of("httpMethod", "GET", "queryStringParameters", Map.of("id", "not-a-uuid")),
        null);
    Map<String, Object> malformedBody = configured.handleRequest(
        Map.of("httpMethod", "POST", "body", "not-json"), null);

    assertEquals(400, invalidId.get("statusCode"));
    assertEquals(400, malformedBody.get("statusCode"));
  }

  private Map<String, Object> request(String x, String y, String width, String height) {
    Map<String, Object> queryParameters = new HashMap<>();
    if (x != null) {
      queryParameters.put("x", x);
    }
    queryParameters.put("y", y);
    queryParameters.put("width", width);
    queryParameters.put("height", height);

    Map<String, Object> event = new HashMap<>();
    event.put("queryStringParameters", queryParameters);
    return lambda.handleRequest(event, null);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> headers(Map<String, Object> response) {
    return (Map<String, Object>) response.get("headers");
  }
}
