package edu.calpoly.lambdalab.edgarddragde;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

class DynamoResultStoreTest {

  private static final String TABLE = "csc5100-results";

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

  @Test
  void saveWritesOneItemKeyedByStringId() {
    StubDynamo dynamo = new StubDynamo();
    String id = UUID.randomUUID().toString();
    String json = "{\"id\":\"" + id + "\",\"distance\":7.0,\"reachable\":true}";

    new DynamoResultStore(dynamo, TABLE).save(id, json);

    assertEquals(1, dynamo.items.size());
    assertEquals(TABLE, dynamo.lastPut.tableName());
    Map<String, AttributeValue> item = dynamo.lastPut.item();
    assertEquals(id, item.get("id").s());
    assertEquals(json, item.get("resultJson").s());
    assertEquals(DynamoResultStore.OWNER, item.get("owner").s());
    assertEquals(DynamoResultStore.FEATURE, item.get("feature").s());
    assertEquals("attribute_not_exists(id)", dynamo.lastPut.conditionExpression());
  }

  @Test
  void getReturnsExactlyWhatWasSaved() {
    DynamoResultStore store = new DynamoResultStore(new StubDynamo(), TABLE);
    String id = UUID.randomUUID().toString();
    String json = "{\"id\":\"" + id + "\",\"distance\":7.0710678118654755,\"reachable\":true}";

    store.save(id, json);

    assertEquals(json, store.get(id));
  }

  @Test
  void getUnknownIdReturnsNull() {
    assertNull(new DynamoResultStore(new StubDynamo(), TABLE).get(UUID.randomUUID().toString()));
  }

  @Test
  void getIgnoresItemsOwnedBySomeoneElse() {
    StubDynamo dynamo = new StubDynamo();
    String id = UUID.randomUUID().toString();
    dynamo.items.put(id, Map.of(
        "id", AttributeValue.fromS(id),
        "owner", AttributeValue.fromS("another-student"),
        "feature", AttributeValue.fromS(DynamoResultStore.FEATURE),
        "resultJson", AttributeValue.fromS("{\"secret\":true}")));

    assertNull(new DynamoResultStore(dynamo, TABLE).get(id));
  }

  @Test
  void getIgnoresItemsFromAnotherFeature() {
    StubDynamo dynamo = new StubDynamo();
    String id = UUID.randomUUID().toString();
    dynamo.items.put(id, Map.of(
        "id", AttributeValue.fromS(id),
        "owner", AttributeValue.fromS(DynamoResultStore.OWNER),
        "feature", AttributeValue.fromS("some-other-lambda"),
        "resultJson", AttributeValue.fromS("{}")));

    assertNull(new DynamoResultStore(dynamo, TABLE).get(id));
  }

  @Test
  void getIgnoresIncompleteItems() {
    StubDynamo dynamo = new StubDynamo();
    String id = UUID.randomUUID().toString();
    dynamo.items.put(id, Map.of("id", AttributeValue.fromS(id)));

    assertNull(new DynamoResultStore(dynamo, TABLE).get(id));
  }

  // ---- through the Lambda handler ----
  @Test
  void postThenGetThroughDynamoReturnsSameBody() throws Exception {
    StubDynamo dynamo = new StubDynamo();
    RobotReachabilityLambda lambda = new RobotReachabilityLambda(new DynamoResultStore(dynamo, TABLE));

    Map<String, Object> post = new HashMap<>();
    post.put("httpMethod", "POST");
    post.put("body", "{\"x\":3,\"y\":4,\"z\":5,\"radius\":10}");
    Map<String, Object> postResponse = lambda.handleRequest(post, null);

    assertEquals(200, postResponse.get("statusCode"));
    assertEquals(1, dynamo.items.size());
    String id = new ObjectMapper().readTree(postResponse.get("body").toString()).get("id").asText();

    Map<String, Object> get = new HashMap<>();
    get.put("httpMethod", "GET");
    get.put("queryStringParameters", Map.of("id", id));
    Map<String, Object> getResponse = lambda.handleRequest(get, null);

    assertEquals(200, getResponse.get("statusCode"));
    assertEquals(postResponse.get("body"), getResponse.get("body"));
  }

  @Test
  void dynamoOutageOnPostIs500AndLeaksNothing() {
    StubDynamo dynamo = new StubDynamo();
    dynamo.failing = true;
    RobotReachabilityLambda lambda = new RobotReachabilityLambda(new DynamoResultStore(dynamo, TABLE));

    Map<String, Object> post = new HashMap<>();
    post.put("httpMethod", "POST");
    post.put("body", "{\"x\":1,\"y\":1,\"z\":1,\"radius\":5}");
    Map<String, Object> response = lambda.handleRequest(post, null);

    assertEquals(500, response.get("statusCode"));
    assertFalse(response.get("body").toString().contains("simulated"));
  }
}
