package edu.calpoly.lambdalab.instructor;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the TemperatureLambda class.
 *
 * @author Javier Gonzalez-Sanchez
 * @version 1.0
 */
class TemperatureLambdaTest {

  @Test
  void convertsFahrenheitToCelsius() {
    TemperatureLambda lambda = new TemperatureLambda();
    Map<String, Object> event = new HashMap<>();
    event.put("body", "{\"value\":32,\"from\":\"F\",\"to\":\"C\"}");
    Map<String, Object> response = lambda.handleRequest(event, null);
    assertEquals(200, response.get("statusCode"));
    assertTrue(response.get("body").toString().contains("\"result\":0.0"));
  }

  @Test
  void convertsCelsiusToFahrenheit() {
    TemperatureLambda lambda = new TemperatureLambda();
    Map<String, Object> event = new HashMap<>();
    event.put("body", "{\"value\":100,\"from\":\"C\",\"to\":\"F\"}");
    Map<String, Object> response = lambda.handleRequest(event, null);
    assertEquals(200, response.get("statusCode"));
    assertTrue(response.get("body").toString().contains("\"result\":212.0"));
  }

  @Test
  void rejectsUnsupportedConversion() {
    TemperatureLambda lambda = new TemperatureLambda();
    Map<String, Object> event = new HashMap<>();
    event.put("body", "{\"value\":100,\"from\":\"C\",\"to\":\"K\"}");
    Map<String, Object> response = lambda.handleRequest(event, null);
    assertEquals(400, response.get("statusCode"));
  }

  @Test
  void convertsFahrenheitToCelsiusUsingGet() {
    TemperatureLambda lambda = new TemperatureLambda();
    Map<String, Object> queryParameters = new HashMap<>();
    queryParameters.put("value", "32");
    queryParameters.put("from", "F");
    queryParameters.put("to", "C");
    Map<String, Object> event = new HashMap<>();
    event.put("queryStringParameters", queryParameters);
    Map<String, Object> response = lambda.handleRequest(event, null);
    assertEquals(200, response.get("statusCode"));
    assertTrue(response.get("body").toString().contains("\"result\":0.0"));
  }

  @Test
  void postPersistsAndGetRetrievesSameId() {
    class FakeStorage implements Storage {
      final Map<String, String> values = new HashMap<>();
      public void save(String id, String json) { values.put(id, json); }
      public String get(String id) { return values.get(id); }
    }
    FakeStorage storage = new FakeStorage();
    TemperatureLambda lambda = new TemperatureLambda(storage);
    String id = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
    Map<String, Object> post = Map.of("httpMethod", "POST", "body",
        "{\"id\":\"" + id + "\",\"value\":32,\"from\":\"F\",\"to\":\"C\"}");
    Map<String, Object> response = lambda.handleRequest(post, null);
    assertEquals(200, response.get("statusCode"));
    assertTrue(storage.values.containsKey(id));
    Map<String, Object> get = Map.of("httpMethod", "GET",
        "queryStringParameters", Map.of("id", id));
    Map<String, Object> fetched = lambda.handleRequest(get, null);
    assertEquals(200, fetched.get("statusCode"));
    assertTrue(fetched.get("body").toString().contains(id));
  }

  @Test
  void rejectsInvalidIdAndReturns404ForMissingRecord() {
    TemperatureLambda lambda = new TemperatureLambda(new Storage() {
      public void save(String id, String json) { }
      public String get(String id) { return null; }
    });
    assertEquals(400, lambda.handleRequest(Map.of("httpMethod", "GET",
        "queryStringParameters", Map.of("id", "bad")), null).get("statusCode"));
    assertEquals(404, lambda.handleRequest(Map.of("httpMethod", "GET",
        "queryStringParameters", Map.of("id",
            "c5f1b1b7-ccf5-4b98-80f9-f1123589a123")), null).get("statusCode"));
  }

  @Test
  void postSendsSameCorrelatedPayloadToIndependentMessagingChannels() {
    class FakeStorage implements Storage {
      public void save(String id, String json) { }
      public String get(String id) { return null; }
    }
    java.util.List<String> messages = new java.util.ArrayList<>();
    TemperatureLambda lambda = new TemperatureLambda(new FakeStorage(), messages::add);
    String id = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
    Map<String, Object> response = lambda.handleRequest(Map.of(
        "httpMethod", "POST", "body",
        "{\"id\":\"" + id + "\",\"value\":32,\"from\":\"F\",\"to\":\"C\"}"), null);
    assertEquals(200, response.get("statusCode"));
    assertEquals(1, messages.size());
    assertTrue(messages.get(0).contains(id));
    assertTrue(response.get("body").toString().contains("\"sns\":\"published\""));
    assertTrue(response.get("body").toString().contains("\"sqs\":\"queued\""));
  }

  @Test
  void legacyGetDoesNotPublishMessages() {
    java.util.List<String> messages = new java.util.ArrayList<>();
    TemperatureLambda lambda = new TemperatureLambda(null, messages::add);
    Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "GET",
        "queryStringParameters", Map.of("value", "32", "from", "F", "to", "C")), null);
    assertEquals(200, response.get("statusCode"));
    assertTrue(messages.isEmpty());
  }

}
