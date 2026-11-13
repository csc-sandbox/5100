package edu.calpoly.lambdalab.briggsmurphy;

import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;

/**
 * AWS implementation of the Storage interface using DynamoDB and S3.
 *
 * @author Briggs Murphy
 * @version 1.0
 */
public class LidarClosestStorage implements Storage {

  private static final String TABLE_NAME = "csc5100-results";
  private static final String BUCKET_NAME = "csc5100-results";
  private static final String S3_PREFIX = "briggsmurphy/LidarClosestLambda/";

  private final DynamoDbClient client;
  private final S3Client s3Client;
  private final RetryExecutor retryExecutor;

  public LidarClosestStorage(DynamoDbClient client, S3Client s3Client) {
    this(client, s3Client, new RetryExecutor(3, failure -> {
      if (failure instanceof SdkClientException) {
        return true;
      }

      if (failure instanceof SdkServiceException) {
        SdkServiceException serviceFailure =
            (SdkServiceException) failure;
        return serviceFailure.isRetryableException()
            || serviceFailure.isThrottlingException();
      }

      return false;
    }, () -> {
      // No delay between attempts.
    }));
  }

  LidarClosestStorage(
      DynamoDbClient client,
      S3Client s3Client,
      RetryExecutor retryExecutor) {
    this.client = client;
    this.s3Client = s3Client;
    this.retryExecutor = retryExecutor;
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

    retryExecutor.execute(() -> s3Client.putObject(
        PutObjectRequest.builder()
            .bucket(BUCKET_NAME)
            .key(key)
            .contentType("application/json")
            .build(),
        RequestBody.fromString(json)));
  }
}

