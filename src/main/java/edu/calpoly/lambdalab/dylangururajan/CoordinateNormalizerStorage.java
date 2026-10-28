package edu.calpoly.lambdalab.dylangururajan;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Persists the same result JSON in the shared A04 DynamoDB table and S3 bucket. */
final class CoordinateNormalizerStorage implements CoordinateNormalizerLambda.Storage {
  static final String OWNER = "dylangururajan";
  static final String FEATURE = "coordinate-normalizer";
  static final String S3_PREFIX = OWNER + "/" + FEATURE + "/";

  private final DynamoDbClient dynamo;
  private final S3Client s3;
  private final String table;
  private final String bucket;

  CoordinateNormalizerStorage(String table, String bucket) {
    this(DynamoDbClient.create(), S3Client.create(), table, bucket);
  }

  CoordinateNormalizerStorage(
      DynamoDbClient dynamo, S3Client s3, String table, String bucket) {
    this.dynamo = dynamo;
    this.s3 = s3;
    this.table = table;
    this.bucket = bucket;
  }

  @Override
  public void save(String id, String json) {
    // S3 and DynamoDB do not support a shared transaction. Store the immutable object first,
    // then write the DynamoDB lookup record used by GET.
    s3.putObject(
        PutObjectRequest.builder()
            .bucket(bucket)
            .key(S3_PREFIX + id + ".json")
            .contentType("application/json")
            .build(),
        RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));
    dynamo.putItem(
        PutItemRequest.builder()
            .tableName(table)
            .item(Map.of(
                "id", AttributeValue.fromS(id),
                "owner", AttributeValue.fromS(OWNER),
                "feature", AttributeValue.fromS(FEATURE),
                "resultJson", AttributeValue.fromS(json)))
            .build());
  }

  @Override
  public String get(String id) {
    var result = dynamo.getItem(
        GetItemRequest.builder()
            .tableName(table)
            .key(Map.of("id", AttributeValue.fromS(id)))
            .consistentRead(true)
            .build());
    if (!result.hasItem() || !result.item().containsKey("resultJson")) {
      return null;
    }
    if (!OWNER.equals(stringAttribute(result.item(), "owner"))
        || !FEATURE.equals(stringAttribute(result.item(), "feature"))) {
      return null;
    }
    return result.item().get("resultJson").s();
  }

  private String stringAttribute(Map<String, AttributeValue> item, String name) {
    AttributeValue value = item.get(name);
    return value == null ? null : value.s();
  }
}
