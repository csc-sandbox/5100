package edu.calpoly.lambdalab.instructor;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Shared A04 storage adapter; the DynamoDB record is the GET source of truth. */
final class TemperatureStorage implements TemperatureLambda.Storage {
  private final DynamoDbClient dynamo = DynamoDbClient.create();
  private final S3Client s3 = S3Client.create();
  private final String table;
  private final String bucket;

  TemperatureStorage(String table, String bucket) {
    this.table = table;
    this.bucket = bucket;
  }

  @Override
  public void save(String id, String json) {
    // Store object first; a failed DynamoDB write can leave an orphan S3 object.
    // Reconciliation/compensation is a later enhancement, not a transaction.
    long s3Start = System.nanoTime();
    try {
    s3.putObject(PutObjectRequest.builder().bucket(bucket)
        .key("instructor/temperature/" + id + ".json")
        .contentType("application/json").build(),
        RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));
    TemperatureObservability.log(id, null, "S3", "S3_SAVED", "SUCCESS", s3Start);
    } catch (RuntimeException e) {
      TemperatureObservability.log(id, null, "S3", "S3_SAVED", "FAILURE", s3Start);
      throw e;
    }
    long dynamoStart = System.nanoTime();
    try {
    dynamo.putItem(PutItemRequest.builder().tableName(table)
        .item(Map.of(
            "id", AttributeValue.fromS(id),
            "owner", AttributeValue.fromS("instructor"),
            "feature", AttributeValue.fromS("temperature"),
            "resultJson", AttributeValue.fromS(json))).build());
    TemperatureObservability.log(id, null, "DynamoDB", "DYNAMODB_SAVED", "SUCCESS", dynamoStart);
    } catch (RuntimeException e) {
      TemperatureObservability.log(id, null, "DynamoDB", "DYNAMODB_SAVED", "FAILURE", dynamoStart);
      throw e;
    }
  }

  @Override
  public String get(String id) {
    var result = dynamo.getItem(GetItemRequest.builder().tableName(table)
        .key(Map.of("id", AttributeValue.fromS(id))).consistentRead(true).build());
    if (!result.hasItem() || !result.item().containsKey("resultJson")) return null;
    if (!"instructor".equals(result.item().get("owner").s())) return null;
    return result.item().get("resultJson").s();
  }
}
