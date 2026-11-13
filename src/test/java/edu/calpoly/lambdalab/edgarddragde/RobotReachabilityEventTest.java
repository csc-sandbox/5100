package edu.calpoly.lambdalab.edgarddragde;

import static org.junit.jupiter.api.Assertions.*;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.calpoly.lambdalab.edgarddragde.RobotReachabilityLambda.EventSink;
import edu.calpoly.lambdalab.edgarddragde.RobotReachabilityLambda.InMemoryResultStore;
import edu.calpoly.lambdalab.edgarddragde.RobotReachabilityLambda.ResultStore;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class RobotReachabilityEventTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String FIXED_ID = "14caa8ee-ca63-41f4-b57f-074007b2affc";
  private static final String GOOD_BODY = "{\"x\":3,\"y\":4,\"z\":5,\"radius\":10}";

  /** Records every event it is asked to publish, or fails on demand. */
  private static final class CapturingSink implements EventSink {
    final List<String> published = new ArrayList<>();
    boolean failing;

    @Override
    public void publish(String json) {
      if (failing) {
        throw new IllegalStateException("simulated SNS outage");
      }
      published.add(json);
    }
  }

  private final InMemoryResultStore store = new InMemoryResultStore();
  private final CapturingSink sns = new CapturingSink();
  private final RobotReachabilityLambda lambda = new RobotReachabilityLambda(store, () -> FIXED_ID, sns);

  // ---- event content ----

  @Test
  void publishedEventCarriesIdOperationAndResult() {
    Map<String, Object> response = lambda.handleRequest(post(GOOD_BODY), null);

    assertEquals(200, response.get("statusCode"));
    assertEquals(1, sns.published.size());
    JsonNode event = tree(sns.published.get(0));
    assertEquals(Set.of("id", "operation", "result"), names(event));
    assertEquals(FIXED_ID, event.get("id").asText());
    assertEquals("RobotReachabilityLambda", event.get("operation").asText());
    assertEquals(Set.of("distance", "reachable"), names(event.get("result")));
    assertEquals(Math.sqrt(50), event.get("result").get("distance").asDouble(), 1e-12);
    assertTrue(event.get("result").get("reachable").asBoolean());
  }

  @Test
  void eventResultMatchesTheStoredRecord() {
    lambda.handleRequest(post(GOOD_BODY), null);

    ObjectNode storedRecord = (ObjectNode) tree(store.get(FIXED_ID));
    JsonNode event = tree(sns.published.get(0));
    assertEquals(storedRecord.get("id"), event.get("id"));
    storedRecord.remove("id");
    assertEquals(storedRecord, event.get("result"));
  }

  // ---- status reporting ----

  @Test
  void responseReportsSnsPublished() {
    Map<String, Object> response = lambda.handleRequest(post(GOOD_BODY), null);

    assertEquals("published", tree(body(response)).get("messaging").get("sns").asText());
  }

  @Test
  void noSinkConfiguredIsReportedAndStillSucceeds() {
    RobotReachabilityLambda noSns = new RobotReachabilityLambda(store, () -> FIXED_ID, null);

    Map<String, Object> response = noSns.handleRequest(post(GOOD_BODY), null);

    assertEquals(200, response.get("statusCode"));
    assertEquals("not_configured", tree(body(response)).get("messaging").get("sns").asText());
    assertEquals(1, store.size());
  }

  // ---- failure handling ----

  @Test
  void snsFailureKeepsTheOperationSuccessfulAndTheResultRetrievable() {
    sns.failing = true;

    Map<String, Object> response = lambda.handleRequest(post(GOOD_BODY), null);

    assertEquals(200, response.get("statusCode"));
    JsonNode json = tree(body(response));
    assertEquals(FIXED_ID, json.get("id").asText());
    assertEquals("failed", json.get("messaging").get("sns").asText());
    assertFalse(body(response).contains("simulated"));
    assertEquals(1, store.size());

    Map<String, Object> get = new HashMap<>();
    get.put("httpMethod", "GET");
    get.put("queryStringParameters", Map.of("id", FIXED_ID));
    assertEquals(200, lambda.handleRequest(get, null).get("statusCode"));
  }

  @Test
  void snsFailureIsLoggedWithTheCorrelationId() {
    sns.failing = true;
    List<String> logs = new ArrayList<>();

    lambda.handleRequest(post(GOOD_BODY), contextCapturing(logs));

    assertEquals(1, logs.size());
    assertTrue(logs.get(0).contains("SNS publish FAILED"), logs.get(0));
    assertTrue(logs.get(0).contains(FIXED_ID), logs.get(0));
  }

  // ---- no event unless the operation really succeeded and was stored ----

  @Test
  void noEventWhenStorageFails() {
    ResultStore failingStore = new ResultStore() {
      @Override public void save(String id, String json) { throw new IllegalStateException("simulated outage"); }
      @Override public String get(String id) { return null; }
    };
    RobotReachabilityLambda failing = new RobotReachabilityLambda(failingStore, () -> FIXED_ID, sns);

    Map<String, Object> response = failing.handleRequest(post(GOOD_BODY), null);

    assertEquals(500, response.get("statusCode"));
    assertTrue(sns.published.isEmpty());
  }

  @Test
  void noEventWhenInputIsInvalid() {
    Map<String, Object> response = lambda.handleRequest(
        post("{\"x\":\"HI\",\"y\":1,\"z\":1,\"radius\":1}"), null);

    assertEquals(400, response.get("statusCode"));
    assertTrue(sns.published.isEmpty());
  }

  @Test
  void legacyGetAndGetByIdNeverPublish() {
    Map<String, Object> legacy = new HashMap<>();
    legacy.put("httpMethod", "GET");
    legacy.put("queryStringParameters", Map.of("x", "1", "y", "1", "z", "1", "radius", "5"));
    assertEquals(200, lambda.handleRequest(legacy, null).get("statusCode"));

    Map<String, Object> byId = new HashMap<>();
    byId.put("httpMethod", "GET");
    byId.put("queryStringParameters", Map.of("id", FIXED_ID));
    assertEquals(404, lambda.handleRequest(byId, null).get("statusCode"));

    assertTrue(sns.published.isEmpty());
  }

  // ---- helpers ----

  private static Map<String, Object> post(String body) {
    Map<String, Object> event = new HashMap<>();
    event.put("httpMethod", "POST");
    event.put("body", body);
    return event;
  }

  private static String body(Map<String, Object> response) {
    return response.get("body").toString();
  }

  private static JsonNode tree(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (Exception e) {
      throw new AssertionError("not JSON: " + json, e);
    }
  }

  private static Set<String> names(JsonNode node) {
    Set<String> names = new TreeSet<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  /** A Context whose only working method is getLogger(); everything else returns null. */
  private static Context contextCapturing(List<String> logs) {
    LambdaLogger logger = new LambdaLogger() {
      @Override
      public void log(String message) {
        logs.add(message);
      }

      @Override
      public void log(byte[] message) {
        logs.add(new String(message, StandardCharsets.UTF_8));
      }
    };
    return (Context) Proxy.newProxyInstance(Context.class.getClassLoader(), new Class<?>[] {Context.class},
        (proxy, method, args) -> "getLogger".equals(method.getName()) ? logger : null);
  }
}
