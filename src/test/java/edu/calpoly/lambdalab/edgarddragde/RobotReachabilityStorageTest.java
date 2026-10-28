package edu.calpoly.lambdalab.edgarddragde;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
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

class RobotReachabilityStorageTest {

  private static final String TABLE = "csc5100-results";
  private static final String BUCKET = "csc5100-results";

  /** Minimal in-memory DynamoDB: only putItem/getItem are implemented. */
  private static final class StubDynamo implements DynamoDbClient {
    final Map<String, Map<String, AttributeValue>> items = new HashMap<>();
    PutItemRequest lastPut;
    boolean failing;

    @Override
    public String serviceName() {
      return "dynamodb";
    }

    @Override
    public void close() { }

    @Override
    public PutItemResponse putItem(PutItemRequest request) {
      if (failing) {
        throw new IllegalStateException("simulated DynamoDB outage");
      }
      lastPut = request;
      items.put(request.item().get("id").s(), request.item());
      return PutItemResponse.builder().build();
    }

    @Override
    public GetItemResponse getItem(GetItemRequest request) {
      if (failing) {
        throw new IllegalStateException("simulated DynamoDB outage");
      }
      Map<String, AttributeValue> item = items.get(request.key().get("id").s());
      return item == null
          ? GetItemResponse.builder().build()
          : GetItemResponse.builder().item(item).build();
    }
  }

  /** Minimal in-memory S3: only putObject is implemented. */
  private static final class StubS3 implements S3Client {
    final Map<String, String> objects = new HashMap<>();
    PutObjectRequest lastPut;
    boolean failing;

    @Override
    public String serviceName() {
      return "s3";
    }

    @Override
    public void close() { }

    @Override
    public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
      if (failing) {
        throw new IllegalStateException("simulated S3 outage");
      }
      try {
        objects.put(request.key(),
            new String(body.contentStreamProvider().newStream().readAllBytes(), StandardCharsets.UTF_8));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      lastPut = request;
      return PutObjectResponse.builder().build();
    }
  }

  private final StubDynamo dynamo = new StubDynamo();
  private final StubS3 s3 = new StubS3();
  private final RobotReachabilityStorage storage = new RobotReachabilityStorage(dynamo, s3, TABLE, BUCKET);

  private static String jsonFor(String id) {
    return "{\"id\":\"" + id + "\",\"distance\":0.37416573867739417,\"reachable\":true}";
  }

  private static Map<String, Object> postEvent(String body) {
    Map<String, Object> event = new HashMap<>();
    event.put("httpMethod", "POST");
    event.put("body", body);
    return event;
  }

  // ---- save ----
  @Test
  void saveWritesIdenticalJsonToS3AndDynamo() {
    String id = UUID.randomUUID().toString();
    String json = jsonFor(id);

    storage.save(id, json);

    assertEquals(1, s3.objects.size());
    assertEquals(BUCKET, s3.lastPut.bucket());
    assertEquals("edgarddragde/robot-reachability/" + id + ".json", s3.lastPut.key());
    assertEquals("application/json", s3.lastPut.contentType());
    assertEquals(json, s3.objects.get(RobotReachabilityStorage.s3KeyFor(id)));

    assertEquals(1, dynamo.items.size());
    assertEquals(TABLE, dynamo.lastPut.tableName());
    Map<String, AttributeValue> item = dynamo.lastPut.item();
    assertEquals(id, item.get("id").s());
    assertEquals(json, item.get("resultJson").s());
    assertEquals("edgarddragde", item.get("owner").s());
    assertEquals("robot-reachability", item.get("feature").s());
    assertEquals("attribute_not_exists(id)", dynamo.lastPut.conditionExpression());
  }

  @Test
  void ifS3FailsDynamoIsNeverWritten() {
    s3.failing = true;

    assertThrows(IllegalStateException.class, () -> storage.save(UUID.randomUUID().toString(), "{}"));

    assertEquals(0, dynamo.items.size());
  }

  @Test
  void ifDynamoFailsS3KeepsAnOrphanButErrorPropagates() {
    dynamo.failing = true;
    String id = UUID.randomUUID().toString();

    assertThrows(IllegalStateException.class, () -> storage.save(id, "{}"));

    assertEquals(1, s3.objects.size());
  }

  // ---- get ----
  @Test
  void getReturnsExactlyWhatWasSaved() {
    String id = UUID.randomUUID().toString();
    storage.save(id, jsonFor(id));

    assertEquals(jsonFor(id), storage.get(id));
  }

  @Test
  void getUnknownIdReturnsNull() {
    assertNull(storage.get(UUID.randomUUID().toString()));
  }

  @Test
  void getIgnoresItemsOwnedBySomeoneElse() {
    String id = UUID.randomUUID().toString();
    dynamo.items.put(id, Map.of(
        "id", AttributeValue.fromS(id),
        "owner", AttributeValue.fromS("another-student"),
        "feature", AttributeValue.fromS("robot-reachability"),
        "resultJson", AttributeValue.fromS("{\"secret\":true}")));

    assertNull(storage.get(id));
  }

  @Test
  void getIgnoresItemsFromAnotherFeature() {
    String id = UUID.randomUUID().toString();
    dynamo.items.put(id, Map.of(
        "id", AttributeValue.fromS(id),
        "owner", AttributeValue.fromS("edgarddragde"),
        "feature", AttributeValue.fromS("some-other-lambda"),
        "resultJson", AttributeValue.fromS("{}")));

    assertNull(storage.get(id));
  }

  @Test
  void getIgnoresIncompleteItems() {
    String id = UUID.randomUUID().toString();
    dynamo.items.put(id, Map.of("id", AttributeValue.fromS(id)));

    assertNull(storage.get(id));
  }

  // ---- through the Lambda handler ----
  @Test
  void postStoresTheResponseBodyInBothPlacesAndGetReturnsIt() throws Exception {
    RobotReachabilityLambda lambda = new RobotReachabilityLambda(storage);

    Map<String, Object> post = lambda.handleRequest(
        postEvent("{\"x\":0.3,\"y\":0.2,\"z\":0.1,\"radius\":0.5}"), null);

    assertEquals(200, post.get("statusCode"));
    String body = post.get("body").toString();
    String id = new ObjectMapper().readTree(body).get("id").asText();
    assertEquals(body, s3.objects.get(RobotReachabilityStorage.s3KeyFor(id)));
    assertEquals(body, dynamo.items.get(id).get("resultJson").s());

    Map<String, Object> get = new HashMap<>();
    get.put("httpMethod", "GET");
    get.put("queryStringParameters", Map.of("id", id));
    Map<String, Object> getResponse = lambda.handleRequest(get, null);
    assertEquals(200, getResponse.get("statusCode"));
    assertEquals(body, getResponse.get("body"));
  }

  @Test
  void s3OutageOnPostIs500AndNothingIsRecordedInDynamo() {
    s3.failing = true;
    RobotReachabilityLambda lambda = new RobotReachabilityLambda(storage);

    Map<String, Object> response = lambda.handleRequest(
        postEvent("{\"x\":1,\"y\":1,\"z\":1,\"radius\":5}"), null);

    assertEquals(500, response.get("statusCode"));
    assertFalse(response.get("body").toString().contains("simulated"));
    assertEquals(0, dynamo.items.size());
  }

  @Test
  void dynamoOutageOnPostIs500AndLeaksNothing() {
    dynamo.failing = true;
    RobotReachabilityLambda lambda = new RobotReachabilityLambda(storage);

    Map<String, Object> response = lambda.handleRequest(
        postEvent("{\"x\":1,\"y\":1,\"z\":1,\"radius\":5}"), null);

    assertEquals(500, response.get("statusCode"));
    assertFalse(response.get("body").toString().contains("simulated"));
  }
}
