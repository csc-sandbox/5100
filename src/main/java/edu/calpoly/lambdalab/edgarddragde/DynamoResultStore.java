package edu.calpoly.lambdalab.edgarddragde;

import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * DynamoDB persistence for RobotReachabilityLambda results.
 *
 * The table is shared by the whole class, so every item carries {@code owner} and
 * {@code feature} attributes and String only returns items that match both.
 * The result JSON is stored as one string attribute so what is read back is exactly
 * what was written.
 *
 * @author Edgard Aviles
 * @version 1.0.0 October 25, 2026
 */
final class DynamoResultStore implements RobotReachabilityLambda.ResultStore {
  static final String OWNER = "edgarddragde";
  static final String FEATURE = "robot-reachability";

  private final DynamoDbClient dynamo;
  private final String table;

  DynamoResultStore(String table) {
    this(DynamoDbClient.create(), table);
  }

  DynamoResultStore(DynamoDbClient dynamo, String table) {
    this.dynamo = dynamo;
    this.table = table;
  }

  @Override
  public void save(String id, String json) {
    dynamo.putItem(PutItemRequest.builder()
        .tableName(table)
        .item(Map.of(
            "id", AttributeValue.fromS(id),
            "owner", AttributeValue.fromS(OWNER),
            "feature", AttributeValue.fromS(FEATURE),
            "resultJson", AttributeValue.fromS(json)))
        // One item per operation: never overwrite an existing id.
        .conditionExpression("attribute_not_exists(id)")
        .build());
  }

  @Override
  public String get(String id) {
    GetItemResponse response = dynamo.getItem(GetItemRequest.builder()
        .tableName(table)
        .key(Map.of("id", AttributeValue.fromS(id)))
        .consistentRead(true)
        .build());
    if (!response.hasItem()) {
      return null;
    }
    Map<String, AttributeValue> item = response.item();
    AttributeValue owner = item.get("owner");
    AttributeValue feature = item.get("feature");
    AttributeValue json = item.get("resultJson");
    if (owner == null || feature == null || json == null
        || !OWNER.equals(owner.s()) || !FEATURE.equals(feature.s())) {
      return null;
    }
    return json.s();
  }
}
