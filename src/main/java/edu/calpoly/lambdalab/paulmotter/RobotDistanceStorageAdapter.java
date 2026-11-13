package edu.calpoly.lambdalab.paulmotter;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

import edu.calpoly.lambdalab.paulmotter.RobotDistanceObservability.Status;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Stores results in S3 and DynamoDB and reads them back from DynamoDB.
 * Each AWS call is retried on its own; all calls are idempotent, so repeating
 * one after a transient failure is safe.
 *
 * @author Paul Motter (PaulMotter)
 * @version 1.1 (2026-11-10)
 */
class RobotDistanceStorageAdapter implements StorageAdapter<RobotDistanceLambdaData> {

    private final S3Client s3Client;
    private final DynamoDbClient dynamoDbClient;
    private final String table;
    private final String bucket;
    private final RetryExecutor retry;

    /**
     * Production constructor: creates AWS clients with SDK retries turned off,
     * so the RetryExecutor is the only retry layer.
     * @param dbTable DynamoDB table name
     * @param s3Bucket S3 bucket name
     */
    RobotDistanceStorageAdapter(String dbTable, String s3Bucket){
        this(dbTable, s3Bucket,
            S3Client.builder().overrideConfiguration(RetryExecutor.withoutSdkRetries()).build(),
            DynamoDbClient.builder().overrideConfiguration(RetryExecutor.withoutSdkRetries()).build());
    }

    /**
     * Constructor that accepts its AWS clients and uses the default retry policy.
     * @param dbTable DynamoDB table name
     * @param s3Bucket S3 bucket name
     * @param s3Client S3 client to use
     * @param dynamoDbClient DynamoDB client to use
     */
    RobotDistanceStorageAdapter(String dbTable, String s3Bucket,
                                S3Client s3Client, DynamoDbClient dynamoDbClient){
        this(dbTable, s3Bucket, s3Client, dynamoDbClient, RetryExecutor.forAws());
    }

    /**
     * Constructor that accepts its AWS clients and retry policy, so tests can supply fakes.
     * @param dbTable DynamoDB table name
     * @param s3Bucket S3 bucket name
     * @param s3Client S3 client to use
     * @param dynamoDbClient DynamoDB client to use
     * @param retry retry policy applied to each AWS call
     */
    RobotDistanceStorageAdapter(String dbTable, String s3Bucket,
                                S3Client s3Client, DynamoDbClient dynamoDbClient, RetryExecutor retry){
        this.table = dbTable;
        this.bucket = s3Bucket;
        this.s3Client = s3Client;
        this.dynamoDbClient = dynamoDbClient;
        this.retry = Objects.requireNonNull(retry);
    }

    @Override
    public void save(RobotDistanceLambdaData data, String json) {
        // Store object first; a failed DynamoDB write can leave an orphan S3 object.
        // Reconciliation/compensation is a later enhancement, not a transaction.

        // S3
        final long s3Start = System.nanoTime();
        try {
            retry.execute(
                () -> s3Client.putObject(PutObjectRequest.builder().bucket(bucket)
                    .key("PaulMotter/DistanceLambda/" + data.id + ".json")
                    .contentType("application/json").build(),
                    RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8))),
                failure -> RobotDistanceObservability.log(data.id, null, "S3", "SAVE RETRY", Status.FAILURE, s3Start, failure));
            RobotDistanceObservability.log(data.id, null, "S3", "SAVED", Status.SUCCESS, s3Start);
        }
        catch (RuntimeException exception) {
            RobotDistanceObservability.log(data.id, null, "S3", "SAVED", Status.FAILURE, s3Start, exception);
            throw exception;
        }

        // DynamoDB
        final long dynamoStart = System.nanoTime();
        try {
            retry.execute(
                () -> dynamoDbClient.putItem(PutItemRequest.builder().tableName(table)
                    .item(Map.of(
                        "id", AttributeValue.fromS(data.id),
                        "operation", AttributeValue.fromS(RobotDistanceLambda.OPERATION_NAME),
                        "owner", AttributeValue.fromS(RobotDistanceLambda.OWNER_NAME),
                        "resultJson", AttributeValue.fromS(json)
                    )).build()),
                failure -> RobotDistanceObservability.log(data.id, null, "DynamoDB", "SAVE RETRY", Status.FAILURE, dynamoStart, failure));
            RobotDistanceObservability.log(data.id, null, "DynamoDB", "SAVED", Status.SUCCESS, dynamoStart);
        }
        catch (RuntimeException exception) {
            RobotDistanceObservability.log(data.id, null, "DynamoDB", "SAVED", Status.FAILURE, dynamoStart, exception);
            throw exception;
        }
    }

    @Override
    public String get(String id) {
        final long startNano = System.nanoTime();
        GetItemResponse result;
        try {
            result = retry.execute(
                () -> dynamoDbClient.getItem(GetItemRequest.builder().tableName(table)
                    .key(Map.of("id", AttributeValue.fromS(id))).consistentRead(true).build()),
                failure -> RobotDistanceObservability.log(id, null, "DynamoDB", "GET RETRY", Status.FAILURE, startNano, failure));
            RobotDistanceObservability.log(id, null, "DynamoDB", "GET", Status.SUCCESS, startNano);
        }
        catch (RuntimeException exception) {
            RobotDistanceObservability.log(id, null, "DynamoDB", "GET", Status.FAILURE, startNano, exception);
            throw exception;
        }

        // Checks that validation fields exist.
        if (!result.hasItem() || !result.item().containsKey("operation") || !result.item().containsKey("owner")) return null;
        // Validate the record's operaiton.
        if (!result.item().get("operation").s().equals(RobotDistanceLambda.OPERATION_NAME)) return null;
        // Validate the record's owner.
        if (!result.item().get("owner").s().equals(RobotDistanceLambda.OWNER_NAME)) return null;

        return result.item().get("resultJson").s();
    }

}
