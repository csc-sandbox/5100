
package edu.calpoly.lambdalab.briggsmurphy;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * AWS Lambda function that finds the closest LiDAR measurement
 * and its index.
 *
 * @author Briggs Murphy
 * @version 1.0
 */
public class LidarClosestLambda
    implements RequestHandler<Map<String, Object>, Map<String, Object>> {

  interface Storage {
    void save(String id, String json);
    String get(String id);
    void saveToS3(String id, String json);
  }

  interface EventPublisher {
    void publish(String json);
  }

  interface QueuePublisher {
    void send(String json);
  }

  private static final String TABLE_NAME = "csc5100-results";
  private static final String BUCKET_NAME = "csc5100-results";
  private static final String S3_PREFIX = "briggsmurphy/LidarClosestLambda/";
  private static final String QUEUE_NAME = "csc5100-processing";

  private final ObjectMapper mapper = new ObjectMapper();
  private final Storage storage;
  private final EventPublisher publisher;
  private final QueuePublisher queuePublisher;

  public LidarClosestLambda() {
    DynamoDbClient dynamoClient = DynamoDbClient.create();
    S3Client s3Client = S3Client.create();
    SnsClient snsClient = SnsClient.create();
    SqsClient sqsClient = SqsClient.create();

    this.storage = new DynamoStorage(dynamoClient, s3Client);
    this.publisher = new SnsPublisher(
        snsClient, System.getenv("SNS_TOPIC_ARN"));
    this.queuePublisher = new SqsPublisher(
        sqsClient, System.getenv("SQS_QUEUE_URL"));
  }

  // Constructor for tests that do not publish to real services.
  LidarClosestLambda(Storage storage) {
    this(storage, json -> {
      // No-op SNS publisher for tests.
    }, json -> {
      // No-op SQS publisher for tests.
    });
  }

  // Constructor that preserves the existing SNS tests.
  LidarClosestLambda(Storage storage, EventPublisher publisher) {
    this(storage, publisher, json -> {
      // No-op SQS publisher for tests.
    });
  }

  // Constructor for testing both publishing branches.
  LidarClosestLambda(
      Storage storage,
      EventPublisher publisher,
      QueuePublisher queuePublisher) {
    this.storage = storage;
    this.publisher = publisher;
    this.queuePublisher = queuePublisher;
  }

  private static class SnsPublisher implements EventPublisher {
    private final SnsClient client;
    private final String topicArn;

    SnsPublisher(SnsClient client, String topicArn) {
      this.client = client;
      this.topicArn = topicArn;
    }

    @Override
    public void publish(String json) {
      if (topicArn == null || topicArn.isBlank()) {
        throw new IllegalStateException(
            "SNS_TOPIC_ARN environment variable is not set");
      }

      client.publish(PublishRequest.builder()
          .topicArn(topicArn)
          .message(json)
          .build());
    }
  }

  private static class SqsPublisher implements QueuePublisher {
    private final SqsClient client;
    private final String queueUrl;

    SqsPublisher(SqsClient client, String queueUrl) {
      this.client = client;
      this.queueUrl = queueUrl;
    }

    @Override
    public void send(String json) {
      if (queueUrl == null || queueUrl.isBlank()) {
        throw new IllegalStateException(
            "SQS_QUEUE_URL environment variable is not set for "
                + QUEUE_NAME);
      }

      client.sendMessage(SendMessageRequest.builder()
          .queueUrl(queueUrl)
          .messageBody(json)
          .build());
    }
  }

  private static class DynamoStorage implements Storage {
    private final DynamoDbClient client;
    private final S3Client s3Client;

    DynamoStorage(DynamoDbClient client, S3Client s3Client) {
      this.client = client;
      this.s3Client = s3Client;
    }

    @Override
    public void save(String id, String json) {
      Map<String, AttributeValue> item = new HashMap<>();
      item.put("id", AttributeValue.builder().s(id).build());
      item.put("result", AttributeValue.builder().s(json).build());

      client.putItem(PutItemRequest.builder()
          .tableName(TABLE_NAME)
          .item(item)
          .build());
    }

    @Override
    public String get(String id) {
      Map<String, AttributeValue> key = Map.of(
          "id", AttributeValue.builder().s(id).build());

      Map<String, AttributeValue> item = client.getItem(
          GetItemRequest.builder()
              .tableName(TABLE_NAME)
              .key(key)
              .consistentRead(true)
              .build())
          .item();

      if (item == null || item.isEmpty()) {
        return null;
      }

      AttributeValue result = item.get("result");
      return result == null ? null : result.s();
    }

    @Override
    public void saveToS3(String id, String json) {
      String key = S3_PREFIX + id + ".json";

      s3Client.putObject(
          PutObjectRequest.builder()
              .bucket(BUCKET_NAME)
              .key(key)
              .contentType("application/json")
              .build(),
          RequestBody.fromString(json));
    }
  }

  private static class SnsPublishException extends RuntimeException {
    SnsPublishException(Exception cause) {
      super(cause);
    }
  }

  private static class SqsPublishException extends RuntimeException {
    SqsPublishException(Exception cause) {
      super(cause);
    }
  }

  @Override
  @SuppressWarnings("unchecked")
  public Map<String, Object> handleRequest(
      Map<String, Object> event, Context context) {
    try {
      if (event == null) {
        return httpResponse(400, "{\"error\":\"Invalid request\"}");
      }

      Map<String, Object> requestContext =
          event.get("requestContext") instanceof Map
              ? (Map<String, Object>) event.get("requestContext")
              : Map.of();

      Map<String, Object> http =
          requestContext.get("http") instanceof Map
              ? (Map<String, Object>) requestContext.get("http")
              : Map.of();

      Map<String, Object> query =
          event.get("queryStringParameters") instanceof Map
              ? (Map<String, Object>) event.get("queryStringParameters")
              : Map.of();

      String method = String.valueOf(event.getOrDefault(
          "httpMethod",
          http.getOrDefault(
              "method",
              event.get("queryStringParameters") != null
                  ? "GET" : "POST")))
          .toUpperCase();

      if ("GET".equals(method)) {
        String id = String.valueOf(query.get("id"));

        if (!isUuid(id)) {
          return httpResponse(400, "{\"error\":\"Invalid id\"}");
        }

        String json = storage.get(id);

        if (json == null) {
          return httpResponse(404, "{\"error\":\"Result not found\"}");
        }

        return httpResponse(200, json);
      }

      if (!"POST".equals(method)) {
        return httpResponse(400, "{\"error\":\"Invalid request\"}");
      }

      if (!(event.get("body") instanceof String)) {
        return httpResponse(400, "{\"error\":\"Missing request body\"}");
      }

      Map<String, Object> input =
          mapper.readValue((String) event.get("body"), Map.class);

      List<?> distances = (List<?>) input.get("distances");

      if (distances == null || distances.isEmpty()) {
        return httpResponse(
            400, "{\"error\":\"Distances array is required\"}");
      }

      double[] values = new double[distances.size()];

      for (int i = 0; i < distances.size(); i++) {
        values[i] = Double.parseDouble(distances.get(i).toString());

        if (!Double.isFinite(values[i])) {
          return httpResponse(
              400, "{\"error\":\"Distances must be finite numbers\"}");
        }
      }

      Map<String, Object> result = findClosest(values);
      String id = UUID.randomUUID().toString();

      result.put("id", id);
      String json = mapper.writeValueAsString(result);

      storage.save(id, json);
      storage.saveToS3(id, json);

      // Task 4: publish independently to SNS using the existing format.
      Map<String, Object> publishedEvent = new HashMap<>();
      publishedEvent.put("uuid", id);
      publishedEvent.put("operation", "LidarClosestLambda");
      publishedEvent.put("result", result);

      String eventJson = mapper.writeValueAsString(publishedEvent);

      try {
        publisher.publish(eventJson);
      } catch (Exception e) {
        throw new SnsPublishException(e);
      }

      // Task 5: publish independently to SQS using the required "id" field.
      Map<String, Object> queueEvent = new HashMap<>();
      queueEvent.put("id", id);
      queueEvent.put("operation", "LidarClosestLambda");
      queueEvent.put("result", result);

      String queueJson = mapper.writeValueAsString(queueEvent);

      try {
        queuePublisher.send(queueJson);
      } catch (Exception e) {
        throw new SqsPublishException(e);
      }

      return httpResponse(200, json);

    } catch (JsonProcessingException | IllegalArgumentException e) {
      return httpResponse(400, "{\"error\":\"Invalid request\"}");

    } catch (SnsPublishException e) {
      if (context != null) {
        context.getLogger().log(
            "SNS publishing failed: " + e.getCause().getMessage());
      }
      return httpResponse(500, "{\"error\":\"SNS publishing failed\"}");

    } catch (SqsPublishException e) {
      if (context != null) {
        context.getLogger().log(
            "SQS publishing failed: " + e.getCause().getMessage());
      }
      return httpResponse(500, "{\"error\":\"SQS publishing failed\"}");

    } catch (Exception e) {
      if (context != null) {
        context.getLogger().log(
            "LidarClosestLambda failure: " + e.getMessage());
      }
      return httpResponse(500, "{\"error\":\"Storage operation failed\"}");
    }
  }

  private boolean isUuid(String id) {
    try {
      return UUID.fromString(id).toString().equals(id);
    } catch (Exception e) {
      return false;
    }
  }

  private Map<String, Object> findClosest(double[] distances) {
    int closestIndex = 0;
    double closestDistance = distances[0];

    for (int i = 1; i < distances.length; i++) {
      if (distances[i] < closestDistance) {
        closestDistance = distances[i];
        closestIndex = i;
      }
    }

    Map<String, Object> result = new HashMap<>();
    result.put("distance", closestDistance);
    result.put("index", closestIndex);
    return result;
  }

  private Map<String, Object> httpResponse(int statusCode, String body) {
    Map<String, Object> response = new HashMap<>();
    response.put("statusCode", statusCode);
    response.put("headers", Map.of("Content-Type", "application/json"));
    response.put("body", body);
    return response;
  }
}
