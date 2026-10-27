
package edu.calpoly.lambdalab.briggsmurphy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LidarClosestLambdaTest {

  private static class FakeStorage implements LidarClosestLambda.Storage {
    final Map<String, String> values = new HashMap<>();
    final Map<String, String> s3Values = new HashMap<>();

    @Override
    public void save(String id, String json) {
      values.put(id, json);
    }

    @Override
    public String get(String id) {
      return values.get(id);
    }

    @Override
    public void saveToS3(String id, String json) {
      s3Values.put(id, json);
    }
  }

  @Test
  void findsClosestDistance() {
    LidarClosestLambda lambda = new LidarClosestLambda(new FakeStorage());

    Map<String, Object> event = new HashMap<>();
    event.put("body", "{\"distances\":[1.4,0.8,2.1,0.5,1.7]}");

    Map<String, Object> response = lambda.handleRequest(event, null);

    assertEquals(200, response.get("statusCode"));
    assertTrue(response.get("body").toString().contains("\"distance\":0.5"));
    assertTrue(response.get("body").toString().contains("\"index\":3"));
  }

  @Test
  void handlesSingleValue() {
    LidarClosestLambda lambda = new LidarClosestLambda(new FakeStorage());

    Map<String, Object> event = new HashMap<>();
    event.put("body", "{\"distances\":[4.2]}");

    Map<String, Object> response = lambda.handleRequest(event, null);

    assertEquals(200, response.get("statusCode"));
    assertTrue(response.get("body").toString().contains("\"distance\":4.2"));
    assertTrue(response.get("body").toString().contains("\"index\":0"));
  }

  @Test
  void rejectsEmptyArray() {
    LidarClosestLambda lambda = new LidarClosestLambda(new FakeStorage());

    Map<String, Object> event = new HashMap<>();
    event.put("body", "{\"distances\":[]}");

    Map<String, Object> response = lambda.handleRequest(event, null);

    assertEquals(400, response.get("statusCode"));
  }

  @Test
  void rejectsInvalidDistance() {
    LidarClosestLambda lambda = new LidarClosestLambda(new FakeStorage());

    Map<String, Object> event = new HashMap<>();
    event.put("body", "{\"distances\":[1.2,\"invalid\",3.4]}");

    Map<String, Object> response = lambda.handleRequest(event, null);

    assertEquals(400, response.get("statusCode"));
  }

  @Test
  void postGeneratesUuidAndSavesResultToDynamoDbAndS3() {
    FakeStorage storage = new FakeStorage();
    LidarClosestLambda lambda = new LidarClosestLambda(storage);

    Map<String, Object> event = new HashMap<>();
    event.put("httpMethod", "POST");
    event.put("body", "{\"distances\":[1.4,0.5,2.1]}");

    Map<String, Object> response = lambda.handleRequest(event, null);
    String body = response.get("body").toString();

    assertEquals(200, response.get("statusCode"));
    assertTrue(body.contains("\"distance\":0.5"));
    assertTrue(body.contains("\"index\":1"));

    String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    assertDoesNotThrow(() -> UUID.fromString(id));

    assertEquals(body, storage.values.get(id));
    assertEquals(body, storage.s3Values.get(id));
    assertTrue(storage.s3Values.containsKey(id));
  }

  @Test
  void getRetrievesSavedResultByUuid() {
    String id = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
    String savedJson =
        "{\"id\":\"" + id + "\",\"distance\":0.5,\"index\":1}";

    LidarClosestLambda.Storage storage = new LidarClosestLambda.Storage() {
      @Override
      public void save(String key, String json) {
      }

      @Override
      public String get(String key) {
        return id.equals(key) ? savedJson : null;
      }

      @Override
      public void saveToS3(String key, String json) {
      }
    };

    LidarClosestLambda lambda = new LidarClosestLambda(storage);

    Map<String, Object> event = Map.of(
        "httpMethod", "GET",
        "queryStringParameters", Map.of("id", id));

    Map<String, Object> response = lambda.handleRequest(event, null);

    assertEquals(200, response.get("statusCode"));
    assertEquals(savedJson, response.get("body"));
  }

  @Test
  void rejectsInvalidGetId() {
    LidarClosestLambda.Storage storage = new LidarClosestLambda.Storage() {
      @Override
      public void save(String id, String json) {
      }

      @Override
      public String get(String id) {
        return null;
      }

      @Override
      public void saveToS3(String id, String json) {
      }
    };

    LidarClosestLambda lambda = new LidarClosestLambda(storage);

    Map<String, Object> event = Map.of(
        "httpMethod", "GET",
        "queryStringParameters", Map.of("id", "not-a-uuid"));

    Map<String, Object> response = lambda.handleRequest(event, null);

    assertEquals(400, response.get("statusCode"));
  }

  @Test
  void getReturns404WhenResultIsMissing() {
    LidarClosestLambda.Storage storage = new LidarClosestLambda.Storage() {
      @Override
      public void save(String id, String json) {
      }

      @Override
      public String get(String id) {
        return null;
      }

      @Override
      public void saveToS3(String id, String json) {
      }
    };

    LidarClosestLambda lambda = new LidarClosestLambda(storage);

    Map<String, Object> event = Map.of(
        "httpMethod", "GET",
        "queryStringParameters",
        Map.of("id", "c5f1b1b7-ccf5-4b98-80f9-f1123589a123"));

    Map<String, Object> response = lambda.handleRequest(event, null);

    assertEquals(404, response.get("statusCode"));
  }

  @Test
  void publishesJsonEventWithUuidOperationAndResult() {
    FakeStorage storage = new FakeStorage();
    String[] publishedEvent = new String[1];

    LidarClosestLambda lambda = new LidarClosestLambda(
        storage, json -> publishedEvent[0] = json);

    Map<String, Object> event = new HashMap<>();
    event.put("httpMethod", "POST");
    event.put("body", "{\"distances\":[1.4,0.5,2.1]}");

    Map<String, Object> response = lambda.handleRequest(event, null);
    String body = response.get("body").toString();
    String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

    assertEquals(200, response.get("statusCode"));
    assertDoesNotThrow(() -> UUID.fromString(id));
    assertTrue(publishedEvent[0].contains("\"uuid\":\"" + id + "\""));
    assertTrue(
        publishedEvent[0].contains("\"operation\":\"LidarClosestLambda\""));
    assertTrue(publishedEvent[0].contains("\"result\":"));
    assertTrue(publishedEvent[0].contains("\"distance\":0.5"));
    assertTrue(publishedEvent[0].contains("\"index\":1"));
  }

  @Test
  void returns500WhenSnsPublishingFails() {
    FakeStorage storage = new FakeStorage();

    LidarClosestLambda lambda = new LidarClosestLambda(
        storage, json -> {
          throw new RuntimeException("SNS unavailable");
        });

    Map<String, Object> event = new HashMap<>();
    event.put("httpMethod", "POST");
    event.put("body", "{\"distances\":[1.4,0.5,2.1]}");

    Map<String, Object> response = lambda.handleRequest(event, null);

    assertEquals(500, response.get("statusCode"));
    assertEquals(
        "{\"error\":\"SNS publishing failed\"}",
        response.get("body"));
  }

  @Test
  void publishesJsonEventToSqsWithIdOperationAndResult() {
    FakeStorage storage = new FakeStorage();
    String[] sentMessage = new String[1];

    LidarClosestLambda lambda = new LidarClosestLambda(
        storage,
        json -> {
          // No-op SNS publisher for this SQS test.
        },
        json -> sentMessage[0] = json);

    Map<String, Object> event = new HashMap<>();
    event.put("httpMethod", "POST");
    event.put("body", "{\"distances\":[1.4,0.5,2.1]}");

    Map<String, Object> response = lambda.handleRequest(event, null);
    String body = response.get("body").toString();
    String id = body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");

    assertEquals(200, response.get("statusCode"));
    assertDoesNotThrow(() -> UUID.fromString(id));

    assertTrue(sentMessage[0].contains("\"id\":\"" + id + "\""));
    assertTrue(
        sentMessage[0].contains("\"operation\":\"LidarClosestLambda\""));
    assertTrue(sentMessage[0].contains("\"result\":"));
    assertTrue(sentMessage[0].contains("\"distance\":0.5"));
    assertTrue(sentMessage[0].contains("\"index\":1"));
    assertTrue(!sentMessage[0].contains("\"uuid\":"));
  }

  @Test
  void returns500WhenSqsPublishingFails() {
    FakeStorage storage = new FakeStorage();

    LidarClosestLambda lambda = new LidarClosestLambda(
        storage,
        json -> {
          // No-op SNS publisher for this SQS test.
        },
        json -> {
          throw new RuntimeException("SQS unavailable");
        });

    Map<String, Object> event = new HashMap<>();
    event.put("httpMethod", "POST");
    event.put("body", "{\"distances\":[1.4,0.5,2.1]}");

    Map<String, Object> response = lambda.handleRequest(event, null);

    assertEquals(500, response.get("statusCode"));
    assertEquals(
        "{\"error\":\"SQS publishing failed\"}",
        response.get("body"));
  }
}
