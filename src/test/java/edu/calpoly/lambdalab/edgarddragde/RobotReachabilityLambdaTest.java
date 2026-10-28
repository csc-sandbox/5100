package edu.calpoly.lambdalab.edgarddragde;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.calpoly.lambdalab.edgarddragde.RobotReachabilityLambda.InMemoryResultStore;
import edu.calpoly.lambdalab.edgarddragde.RobotReachabilityLambda.ResultStore;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;


class RobotReachabilityLambdaTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String GOOD_BODY = "{\"x\":3,\"y\":4,\"z\":5,\"radius\":10}";

  // ---- Original tests (unchanged): they send no httpMethod, so they exercise the legacy GET path ----

  @Test
  void insideRadius() {
    RobotReachabilityLambda lambda = new RobotReachabilityLambda();
    Map<String, Object> event = new HashMap<>();
    Map<String, Object> param = new HashMap<>();
    param.put("x", "3"); param.put("y", "4"); param.put("z", "5"); param.put("radius", "10");
    event.put("queryStringParameters", param);
    Map<String, Object> response = lambda.handleRequest(event, null);
    assertEquals(200, response.get("statusCode"));
    assertTrue(response.get("body").toString().contains("\"reachable\":true"));
  }


  @Test
  void onRadius() {
    RobotReachabilityLambda lambda = new RobotReachabilityLambda();
    Map<String, Object> event = new HashMap<>();
    Map<String, Object> param = new HashMap<>();

    param.put("x", "0.5"); param.put("y", "1"); param.put("z", "1"); param.put("radius", "1.5");
    event.put("queryStringParameters", param);
    Map<String, Object> response = lambda.handleRequest(event, null);
    assertEquals(200, response.get("statusCode"));
    assertTrue(response.get("body").toString().contains("\"reachable\":true"));
  }

  @Test
  void invalidInput() {
    RobotReachabilityLambda lambda = new RobotReachabilityLambda();
    Map<String, Object> event = new HashMap<>();
    Map<String, Object> param = new HashMap<>();

    param.put("x", "HI"); param.put("y", "1"); param.put("z", "1"); param.put("radius", "1.5");
    event.put("queryStringParameters", param);
    Map<String, Object> response = lambda.handleRequest(event, null);
    assertEquals(400, response.get("statusCode"));
  }

  // ---- Legacy GET contract ----

  @Test
  void legacyGetReturnsOnlyDistanceAndReachable() throws Exception {
    Map<String, Object> query = new HashMap<>();
    query.put("x", "3"); query.put("y", "4"); query.put("z", "5"); query.put("radius", "10");
    Map<String, Object> response = new RobotReachabilityLambda().handleRequest(getEvent(query), null);

    assertEquals(200, response.get("statusCode"));
    JsonNode json = MAPPER.readTree(body(response));
    Set<String> names = new TreeSet<>();
    json.fieldNames().forEachRemaining(names::add);
    assertEquals(Set.of("distance", "reachable"), names);
    assertEquals(Math.sqrt(50), json.get("distance").asDouble(), 1e-12);
  }

  @Test
  void legacyGetWithoutQueryParametersIs400() {
    Map<String, Object> event = new HashMap<>();
    event.put("httpMethod", "GET");
    assertEquals(400, new RobotReachabilityLambda().handleRequest(event, null).get("statusCode"));
  }

  @Test
  void legacyGetDoesNotStoreAnything() {
    InMemoryResultStore store = new InMemoryResultStore();
    Map<String, Object> query = new HashMap<>();
    query.put("x", "1"); query.put("y", "1"); query.put("z", "1"); query.put("radius", "5");
    new RobotReachabilityLambda(store).handleRequest(getEvent(query), null);
    assertEquals(0, store.size());
  }

  @Test
  void nullEventIs400() {
    assertEquals(400, new RobotReachabilityLambda().handleRequest(null, null).get("statusCode"));
  }

  // ---- POST ----

  @Test
  void postReturnsUuidAndOriginalFields() throws Exception {
    Map<String, Object> response = new RobotReachabilityLambda().handleRequest(postEvent(GOOD_BODY), null);

    assertEquals(200, response.get("statusCode"));
    JsonNode json = MAPPER.readTree(body(response));
    String id = json.get("id").asText();
    assertEquals(UUID.fromString(id).toString(), id);
    assertEquals(Math.sqrt(50), json.get("distance").asDouble(), 1e-12);
    assertTrue(json.get("reachable").asBoolean());
  }

  @Test
  void postOnBoundaryIsReachable() throws Exception {
    Map<String, Object> response = new RobotReachabilityLambda()
        .handleRequest(postEvent("{\"x\":0.5,\"y\":1,\"z\":1,\"radius\":1.5}"), null);
    assertEquals(200, response.get("statusCode"));
    assertTrue(MAPPER.readTree(body(response)).get("reachable").asBoolean());
  }

  @Test
  void postOutsideRadiusIsNotReachable() throws Exception {
    Map<String, Object> response = new RobotReachabilityLambda()
        .handleRequest(postEvent("{\"x\":10,\"y\":10,\"z\":10,\"radius\":1}"), null);
    assertEquals(200, response.get("statusCode"));
    assertFalse(MAPPER.readTree(body(response)).get("reachable").asBoolean());
  }

  @Test
  void eachPostGetsItsOwnUuid() throws Exception {
    RobotReachabilityLambda lambda = new RobotReachabilityLambda();
    String first = MAPPER.readTree(body(lambda.handleRequest(postEvent(GOOD_BODY), null))).get("id").asText();
    String second = MAPPER.readTree(body(lambda.handleRequest(postEvent(GOOD_BODY), null))).get("id").asText();
    assertNotEquals(first, second);
  }

  @Test
  void postUsesInjectedIdAndStoresIt() {
    String fixed = "14caa8ee-ca63-41f4-b57f-074007b2affc";
    InMemoryResultStore store = new InMemoryResultStore();
    RobotReachabilityLambda lambda = new RobotReachabilityLambda(store, () -> fixed);

    Map<String, Object> response = lambda.handleRequest(postEvent(GOOD_BODY), null);

    assertEquals(200, response.get("statusCode"));
    assertEquals(1, store.size());
    assertEquals(body(response), store.get(fixed));
  }

  @Test
  void postWorksWithHttpApiEventShape() {
    Map<String, Object> event = new HashMap<>();
    event.put("requestContext", Map.of("http", Map.of("method", "POST")));
    event.put("body", GOOD_BODY);
    assertEquals(200, new RobotReachabilityLambda().handleRequest(event, null).get("statusCode"));
  }

  @Test
  void invalidPostBodiesAre400AndStoreNothing() {
    String[] bad = {
      "{\"x\":\"HI\",\"y\":1,\"z\":1,\"radius\":1.5}",   // nonnumeric
      "{\"x\":1,\"y\":1,\"z\":1}",                       // missing radius
      "{\"x\":1,\"y\":1,\"z\":1,\"radius\":-1}",         // negative radius
      "{\"x\":\"NaN\",\"y\":1,\"z\":1,\"radius\":1}",    // non-finite input
      "{\"x\":\"Infinity\",\"y\":1,\"z\":1,\"radius\":1}",
      "not json",
      "[]",
      "null",
      "   "
    };
    for (String badBody : bad) {
      InMemoryResultStore store = new InMemoryResultStore();
      Map<String, Object> response = new RobotReachabilityLambda(store).handleRequest(postEvent(badBody), null);
      assertEquals(400, response.get("statusCode"), "body: " + badBody);
      assertEquals(0, store.size(), "body: " + badBody);
    }
  }

  @Test
  void postWithNoBodyIs400() {
    Map<String, Object> event = new HashMap<>();
    event.put("httpMethod", "POST");
    assertEquals(400, new RobotReachabilityLambda().handleRequest(event, null).get("statusCode"));
  }

  @Test
  void postWithOverflowingDistanceIs400AndStoresNothing() {
    InMemoryResultStore store = new InMemoryResultStore();
    Map<String, Object> response = new RobotReachabilityLambda(store)
        .handleRequest(postEvent("{\"x\":1e200,\"y\":0,\"z\":0,\"radius\":1e300}"), null);
    assertEquals(400, response.get("statusCode"));
    assertEquals(0, store.size());
  }

  @Test
  void storageFailureIs500WithoutLeakingDetails() {
    ResultStore failing = new ResultStore() {
      @Override public void save(String id, String json) { throw new IllegalStateException("boom-secret"); }
      @Override public String get(String id) { return null; }
    };
    Map<String, Object> response = new RobotReachabilityLambda(failing).handleRequest(postEvent(GOOD_BODY), null);
    assertEquals(500, response.get("statusCode"));
    assertTrue(body(response).contains("Operation failed"));
    assertFalse(body(response).contains("boom-secret"));
  }

  // ---- GET by UUID ----

  @Test
  void getByIdReturnsExactlyWhatPostReturned() {
    RobotReachabilityLambda lambda = new RobotReachabilityLambda();
    String postBody = body(lambda.handleRequest(postEvent(GOOD_BODY), null));
    String id = idOf(postBody);

    Map<String, Object> response = lambda.handleRequest(getEvent(Map.of("id", id)), null);

    assertEquals(200, response.get("statusCode"));
    assertEquals(postBody, body(response));
  }

  @Test
  void getUnknownIdIs404() {
    Map<String, Object> response = new RobotReachabilityLambda()
        .handleRequest(getEvent(Map.of("id", UUID.randomUUID().toString())), null);
    assertEquals(404, response.get("statusCode"));
  }

  @Test
  void getMalformedOrNonCanonicalIdIs400() {
    RobotReachabilityLambda lambda = new RobotReachabilityLambda();
    String[] badIds = {"not-a-uuid", "", UUID.randomUUID().toString().toUpperCase()};
    for (String badId : badIds) {
      assertEquals(400, lambda.handleRequest(getEvent(Map.of("id", badId)), null).get("statusCode"),
          "id: " + badId);
    }
  }

  // ---- helpers ----

  private static Map<String, Object> getEvent(Map<String, Object> query) {
    Map<String, Object> event = new HashMap<>();
    event.put("httpMethod", "GET");
    event.put("queryStringParameters", query);
    return event;
  }

  private static Map<String, Object> postEvent(String body) {
    Map<String, Object> event = new HashMap<>();
    event.put("httpMethod", "POST");
    event.put("body", body);
    return event;
  }

  private static String body(Map<String, Object> response) {
    return response.get("body").toString();
  }

  private static String idOf(String json) {
    try {
      return MAPPER.readTree(json).get("id").asText();
    } catch (Exception e) {
      throw new AssertionError("response was not JSON with an id: " + json, e);
    }
  }
}
