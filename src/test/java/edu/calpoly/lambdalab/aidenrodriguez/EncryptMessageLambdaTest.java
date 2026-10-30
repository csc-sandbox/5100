package edu.calpoly.lambdalab.aidenrodriguez;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the EncryptMessageLambda class.
 *
 * @author Aiden Rodriguez
 * @version 1.0
 */
class EncryptMessageLambdaTest {

  @Test
  void encryptsHelloWithShiftThree() {
    EncryptMessageLambda lambda = new EncryptMessageLambda();
    Map<String, Object> event = Map.of("httpMethod", "POST",
      "body", "{\"message\":\"Hello\",\"shift\":\"3\"}");
    Map<String, Object> response = lambda.handleRequest(event, null);
    assertEquals(200, response.get("statusCode"));
    assertTrue(response.get("body").toString().contains("\"original\":\"Hello\""));
    assertTrue(response.get("body").toString().contains("\"encrypted\":\"Khoor\""));
    assertTrue(response.get("body").toString().contains("\"shift\":3"));
    assertTrue(response.get("body").toString().contains("\"id\":\""));
  }

  @Test
  void wrapsXyzAroundToAbc() {
    EncryptMessageLambda lambda = new EncryptMessageLambda();
    Map<String, Object> queryParameters = new HashMap<>();
    queryParameters.put("message", "XYZ");
    queryParameters.put("shift", "3");
    Map<String, Object> event = new HashMap<>();
    event.put("queryStringParameters", queryParameters);
    Map<String, Object> response = lambda.handleRequest(event, null);
    assertEquals(200, response.get("statusCode"));
    assertTrue(response.get("body").toString().contains("\"encrypted\":\"ABC\""));
  }

  @Test
  void rejectsMissingMessage() {
    EncryptMessageLambda lambda = new EncryptMessageLambda();
    Map<String, Object> queryParameters = new HashMap<>();
    queryParameters.put("shift", "3");
    Map<String, Object> event = new HashMap<>();
    event.put("queryStringParameters", queryParameters);
    Map<String, Object> response = lambda.handleRequest(event, null);
    assertEquals(400, response.get("statusCode"));
  }

  @Test
  void rejectsNonNumericShift() {
    EncryptMessageLambda lambda = new EncryptMessageLambda();
    Map<String, Object> queryParameters = new HashMap<>();
    queryParameters.put("message", "Hello");
    queryParameters.put("shift", "abc");
    Map<String, Object> event = new HashMap<>();
    event.put("queryStringParameters", queryParameters);
    Map<String, Object> response = lambda.handleRequest(event, null);
    assertEquals(400, response.get("statusCode"));
  }

  @Test
  void legacyGetReturnsEncryptionWithoutStorageOrMessagingSideEffects() {
    List<String> published = new ArrayList<>();
    int[] storageCalls = {0};
    EncryptMessageLambda.Storage storage = new EncryptMessageLambda.Storage() {
      public void save(String id, String json) { storageCalls[0]++; }
      public String get(String id) { storageCalls[0]++; return null; }
    };
    EncryptMessageLambda lambda = new EncryptMessageLambda(storage, published::add, published::add);

    Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "GET",
        "queryStringParameters", Map.of("message", "Hello", "shift", "3")), null);

    assertEquals(200, response.get("statusCode"));
    assertEquals("{\"original\":\"Hello\",\"encrypted\":\"Khoor\",\"shift\":3}",
        response.get("body"));
    assertEquals(0, storageCalls[0]);
    assertTrue(published.isEmpty());
  }

  @Test
  void postEncryptsAndGetByUuidReturnsTheSameStoredResult() {
    EncryptMessageLambda lambda = new EncryptMessageLambda();
    Map<String, Object> created = lambda.handleRequest(Map.of(
        "httpMethod", "POST", "body", "{\"message\":\"Hello\",\"shift\":\"3\"}"), null);

    assertEquals(200, created.get("statusCode"));
    String body = created.get("body").toString();
    String id = body.replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
    assertEquals(36, id.length());
    UUID.fromString(id);

    Map<String, Object> retrieved = lambda.handleRequest(Map.of("httpMethod", "GET",
        "queryStringParameters", Map.of("id", id)), null);
    assertEquals(200, retrieved.get("statusCode"));
    assertEquals(body, retrieved.get("body"));
  }

  @Test
  void rejectsInvalidLookupUuid() {
    EncryptMessageLambda lambda = new EncryptMessageLambda();
    Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "GET",
        "queryStringParameters", Map.of("id", "not-a-uuid")), null);
    assertEquals(400, response.get("statusCode"));
  }

  @Test
  void storesOneUuidKeyedRecordForEachSuccessfulOperation() {
    class FakeStorage implements EncryptMessageLambda.Storage {
      final Map<String, String> values = new HashMap<>();
      public void save(String id, String json) { values.put(id, json); }
      public String get(String id) { return values.get(id); }
    }
    FakeStorage storage = new FakeStorage();
    EncryptMessageLambda lambda = new EncryptMessageLambda(storage);

    Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "POST", "body",
        "{\"message\":\"Test\",\"shift\":\"1\"}"), null);
    assertEquals(200, response.get("statusCode"));
    String body = response.get("body").toString();
    String id = body.replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
    assertEquals(1, storage.values.size());
    assertEquals(body, storage.values.get(id));
  }

  @Test
  void reportsUsefulErrorWhenPersistenceFails() {
    EncryptMessageLambda lambda = new EncryptMessageLambda(new EncryptMessageLambda.Storage() {
      public void save(String id, String json) { throw new IllegalStateException("DynamoDB unavailable"); }
      public String get(String id) { return null; }
    });
    Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "POST", "body",
        "{\"message\":\"Test\",\"shift\":\"1\"}"), null);
    assertEquals(500, response.get("statusCode"));
    assertTrue(response.get("body").toString().contains("Unable to persist result"));
  }

  @Test
  void publishesCorrelatedEventToSnsAfterPersistence() {
    List<String> events = new ArrayList<>();
    EncryptMessageLambda.Storage storage = new EncryptMessageLambda.Storage() {
      public void save(String id, String json) { }
      public String get(String id) { return null; }
    };
    EncryptMessageLambda lambda = new EncryptMessageLambda(storage, events::add);

    Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "POST", "body",
        "{\"message\":\"Hello\",\"shift\":\"3\"}"), null);
    assertEquals(200, response.get("statusCode"));
    assertEquals(1, events.size());
    String responseBody = response.get("body").toString();
    String id = responseBody.replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
    assertTrue(events.get(0).contains("\"id\":\"" + id + "\""));
    assertTrue(events.get(0).contains("\"operation\":\"EncryptMessageLambda\""));
    assertTrue(events.get(0).contains("\"result\""));
    assertTrue(responseBody.contains("\"sns\":\"published\""));
  }

  @Test
  void returnsUsefulErrorWhenSnsPublishingFails() {
    EncryptMessageLambda.Storage storage = new EncryptMessageLambda.Storage() {
      public void save(String id, String json) { }
      public String get(String id) { return null; }
    };
    EncryptMessageLambda lambda = new EncryptMessageLambda(storage, json -> {
      throw new IllegalStateException("SNS unavailable");
    });
    Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "POST", "body",
        "{\"message\":\"Hello\",\"shift\":\"3\"}"), null);
    assertEquals(502, response.get("statusCode"));
    assertTrue(response.get("body").toString().contains("Unable to deliver event"));
  }

  @Test
  void queuesSameCorrelatedRoutingEnvelopeIndependentlyOfSns() {
    List<String> snsEvents = new ArrayList<>();
    List<String> sqsEvents = new ArrayList<>();
    EncryptMessageLambda.Storage storage = new EncryptMessageLambda.Storage() {
      public void save(String id, String json) { }
      public String get(String id) { return null; }
    };
    EncryptMessageLambda lambda = new EncryptMessageLambda(storage, snsEvents::add, sqsEvents::add);

    Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "POST", "body",
        "{\"message\":\"Hello\",\"shift\":\"3\"}"), null);
    assertEquals(200, response.get("statusCode"));
    assertEquals(1, snsEvents.size());
    assertEquals(1, sqsEvents.size());
    assertEquals(snsEvents.get(0), sqsEvents.get(0));
    String id = response.get("body").toString()
        .replaceFirst(".*\\\"id\\\":\\\"([^\\\"]+)\\\".*", "$1");
    assertTrue(sqsEvents.get(0).contains("\"id\":\"" + id + "\""));
    assertTrue(sqsEvents.get(0).contains("\"operation\":\"EncryptMessageLambda\""));
    assertTrue(sqsEvents.get(0).contains("\"result\""));
    assertTrue(response.get("body").toString().contains("\"sqs\":\"queued\""));
  }

  @Test
  void attemptsSqsWhenIndependentSnsBranchFails() {
    List<String> sqsEvents = new ArrayList<>();
    EncryptMessageLambda.Storage storage = new EncryptMessageLambda.Storage() {
      public void save(String id, String json) { }
      public String get(String id) { return null; }
    };
    EncryptMessageLambda lambda = new EncryptMessageLambda(storage, json -> {
      throw new IllegalStateException("SNS unavailable");
    }, sqsEvents::add);

    Map<String, Object> response = lambda.handleRequest(Map.of("httpMethod", "POST", "body",
        "{\"message\":\"Hello\",\"shift\":\"3\"}"), null);
    assertEquals(502, response.get("statusCode"));
    assertEquals(1, sqsEvents.size());
  }
}
