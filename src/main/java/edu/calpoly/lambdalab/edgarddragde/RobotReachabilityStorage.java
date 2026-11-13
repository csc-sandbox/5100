package edu.calpoly.lambdalab.edgarddragde;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * A04 storage adapter for RobotReachabilityLambda; the DynamoDB record is the GET source of truth.
 *
 * <p>Both the S3 bucket and the DynamoDB table are shared by the whole class, so every S3 key
 * lives under {@code edgarddragde/robot-reachability/} and every DynamoDB item carries
 * {@code owner} and {@code feature} attributes that {@link #get(String)} checks.
 */
final class RobotReachabilityStorage implements RobotReachabilityLambda.ResultStore {
  static final String OWNER = "edgarddragde";
  static final String FEATURE = "robot-reachability";
  static final String S3_PREFIX = OWNER + "/" + FEATURE + "/";

  private final DynamoDbClient dynamo;
  private final S3Client s3;
  private final String table;
  private final String bucket;

  RobotReachabilityStorage(String table, String bucket) {
    this(DynamoDbClient.create(), S3Client.create(), table, bucket);
  }

  /** Clients are injectable so unit tests can run without AWS. */
  RobotReachabilityStorage(DynamoDbClient dynamo, S3Client s3, String table, String bucket) {
    this.dynamo = dynamo;
    this.s3 = s3;
    this.table = table;
    this.bucket = bucket;
  }

  static String s3KeyFor(String id) {
    return S3_PREFIX + id + ".json";
  }

  @Override
  public void save(String id, String json) {
    // Store the object first; a failed DynamoDB write can leave an orphan S3 object,
    // but never a DynamoDB record whose S3 object is missing. This is not a transaction.
    s3.putObject(PutObjectRequest.builder()
            .bucket(bucket)
            .key(s3KeyFor(id))
            .contentType("application/json")
            .build(),
        RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));
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
