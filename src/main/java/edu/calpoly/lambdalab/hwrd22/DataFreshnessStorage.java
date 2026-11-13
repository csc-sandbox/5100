package edu.calpoly.lambdalab.hwrd22;

import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Saves Data Freshness results in DynamoDB table and S3 bucket
 *
 * @author Howard Jiang (hwrd22)
 * @version 1.1 (November 9, 2026)
 */
final class DataFreshnessStorage implements Storage {
    static final String OWNER = "hwrd22";
    static final String FEATURE = "data-freshness";

    private final DynamoDbClient dynamoDb;
    private final S3Client s3;
    private final String table;
    private final String bucket;
    private final RetryExecutor retryExecutor;

    DataFreshnessStorage(String table, String bucket) {
        this(DynamoDbClient.create(), S3Client.builder()
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .retryPolicy(RetryPolicy.none()).build()).build(), table, bucket, new RetryExecutor(3, failure -> {
            if (failure instanceof SdkClientException) {
                return true;
            }

            if (failure instanceof SdkServiceException) {
                SdkServiceException serviceFailure = (SdkServiceException) failure;
                return serviceFailure.isRetryableException() || serviceFailure.isThrottlingException();
            }

            return false;
        }, () -> {}));
    }

    DataFreshnessStorage(DynamoDbClient dynamoDb, S3Client s3, String table, String bucket, RetryExecutor retryExecutor) {
        this.dynamoDb = dynamoDb;
        this.s3 = s3;
        this.table = table;
        this.bucket = bucket;
        this.retryExecutor = retryExecutor;
    }

    @Override
    public void save(String id, String json) {
        long s3Start = System.nanoTime();
        try {
            retryExecutor.execute(() -> s3.putObject(PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(OWNER + "/" + FEATURE + "/" + id + ".json")
                    .contentType("application/json")
                    .build(), RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8))));

            DataFreshnessObservability.log(id, null, "S3", "S3_SAVED", "SUCCESS", s3Start);
        } catch (RuntimeException e) {
            DataFreshnessObservability.log(id, null, "S3", "S3_SAVED", "FAILURE", s3Start);
            throw e;
        }

        long dynamoStart = System.nanoTime();
        try {
            dynamoDb.putItem(PutItemRequest.builder()
                    .tableName(table)
                    .item(Map.of(
                            "id", AttributeValue.fromS(id),
                            "owner", AttributeValue.fromS(OWNER),
                            "feature", AttributeValue.fromS(FEATURE),
                            "resultJson", AttributeValue.fromS(json)
                    )).build());
            DataFreshnessObservability.log(id, null, "DYNAMODB", "DYNAMODB_SAVED", "SUCCESS", dynamoStart);
        } catch (RuntimeException e) {
            DataFreshnessObservability.log(id, null, "DYNAMODB", "DYNAMODB_SAVED", "FAILURE", dynamoStart);
            throw e;
        }

    }

    @Override
    public String get(String id) {
        GetItemResponse response = dynamoDb.getItem(GetItemRequest.builder()
                .tableName(table)
                .key(Map.of("id", AttributeValue.fromS(id)))
                .consistentRead(true).build());

        if (!response.hasItem()) {
            return null;
        }

        AttributeValue owner = response.item().get("owner");
        if (owner == null || !OWNER.equals(owner.s())) {
            return null;
        }

        AttributeValue json = response.item().get("resultJson");
        return json == null ? null : json.s();
    }
}
