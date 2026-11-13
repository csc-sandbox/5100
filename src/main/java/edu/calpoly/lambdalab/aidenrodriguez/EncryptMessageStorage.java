package edu.calpoly.lambdalab.aidenrodriguez;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Persistent storage for EncryptMessageLambda results.
 */
final class EncryptMessageStorage implements EncryptMessageLambda.Storage {
    private static final String OWNER = "aidenrodriguez";
    private static final String FEATURE = "encrypt";
    private static final String S3_PREFIX = "aidenrodriguez/encrypt/";

    private final DynamoDbClient dynamo;
    private final S3Client s3;
    private final String table;
    private final String bucket;

    EncryptMessageStorage(String table, String bucket) {
        this(DynamoDbClient.create(), S3Client.create(), table, bucket);
    }

    EncryptMessageStorage(DynamoDbClient dynamo, S3Client s3, String table, String bucket) {
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("DynamoDB table is required");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("S3 bucket is required");
        }
        this.dynamo = dynamo;
        this.s3 = s3;
        this.table = table;
        this.bucket = bucket;
    }

    @Override
    public void save(String id, String json) {
        // S3 and DynamoDB do not share a transaction; a DynamoDB failure can
        // leave an S3 object for later reconciliation, matching the reference.
        s3.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(S3_PREFIX + id + ".json")
                .contentType("application/json")
                .build(), RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));
        dynamo.putItem(PutItemRequest.builder().tableName(table)
                .item(Map.of(
                        "id", AttributeValue.fromS(id),
                        "owner", AttributeValue.fromS(OWNER),
                        "feature", AttributeValue.fromS(FEATURE),
                        "resultJson", AttributeValue.fromS(json)))
                .build());
    }

    @Override
    public String get(String id) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder()
                .tableName(table)
                .key(Map.of("id", AttributeValue.fromS(id)))
                .consistentRead(true)
                .build())
                .item();
        if (item == null || !item.containsKey("resultJson")) {
            return null;
        }
        if (!OWNER.equals(item.getOrDefault("owner", AttributeValue.fromS("")).s())) {
            return null;
        }
        return item.get("resultJson").s();
    }
}
