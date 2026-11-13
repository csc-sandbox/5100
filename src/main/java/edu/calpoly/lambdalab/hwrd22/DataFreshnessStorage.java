package edu.calpoly.lambdalab.hwrd22;

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
 * @author hwrd22
 * @version 1.0 (October 27, 2026)
 */
final class DataFreshnessStorage implements DataFreshnessLambda.Storage {
    static final String OWNER = "hwrd22";
    static final String FEATURE = "data-freshness";

    private final DynamoDbClient dynamoDb;
    private final S3Client s3;
    private final String table;
    private final String bucket;

    DataFreshnessStorage(String table, String bucket) {
        this(DynamoDbClient.create(), S3Client.create(), table, bucket);
    }

    DataFreshnessStorage(DynamoDbClient dynamoDb, S3Client s3, String table, String bucket) {
        this.dynamoDb = dynamoDb;
        this.s3 = s3;
        this.table = table;
        this.bucket = bucket;
    }

    @Override
    public void save(String id, String json) {
        s3.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(OWNER + "/" + FEATURE + "/" + id + ".json")
                .contentType("application/json")
                .build(), RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));

        dynamoDb.putItem(PutItemRequest.builder()
                .tableName(table)
                .item(Map.of(
                        "id", AttributeValue.fromS(id),
                        "owner", AttributeValue.fromS(OWNER),
                        "feature", AttributeValue.fromS(FEATURE),
                        "resultJson", AttributeValue.fromS(json)
                )).build());
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
