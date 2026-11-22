package edu.calpoly.lambdalab.jjuangon03;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Durable A04 storage adapter; DynamoDB is the strongly consistent GET source. */
final class GazeClusterStorage implements GazeClusterLambda.Storage {
    static final String OWNER = "jjuangon03";
    static final String FEATURE = "gaze-clusters";
    static final String S3_PREFIX = OWNER + "/" + FEATURE + "/";
    private final DynamoDbClient dynamo;
    private final S3Client s3;
    private final String table;
    private final String bucket;

    GazeClusterStorage(String table, String bucket) { this(DynamoDbClient.create(), S3Client.create(), table, bucket); }
    GazeClusterStorage(DynamoDbClient dynamo, S3Client s3, String table, String bucket) {
        this.dynamo = dynamo; this.s3 = s3; this.table = table; this.bucket = bucket;
    }
    @Override public void save(String id, String json) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket).key(S3_PREFIX + id + ".json")
                        .contentType("application/json").build(),
                RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));
        dynamo.putItem(PutItemRequest.builder().tableName(table).item(Map.of(
                "id", AttributeValue.fromS(id), "owner", AttributeValue.fromS(OWNER),
                "feature", AttributeValue.fromS(FEATURE), "resultJson", AttributeValue.fromS(json))).build());
    }
    @Override public String get(String id) {
        Map<String, AttributeValue> item = dynamo.getItem(GetItemRequest.builder().tableName(table)
                .key(Map.of("id", AttributeValue.fromS(id))).consistentRead(true).build()).item();
        if (item == null || !OWNER.equals(value(item, "owner")) || !FEATURE.equals(value(item, "feature"))) return null;
        return value(item, "resultJson");
    }
    private String value(Map<String, AttributeValue> item, String name) {
        AttributeValue attribute = item.get(name); return attribute == null ? null : attribute.s();
    }
}
