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

/** Persists the same result document in S3 and DynamoDB under this student's prefix. */
final class GazeClusterStorage implements GazeClusterLambda.Storage {
    private static final String OWNER = "jjuangon03";
    private static final String FEATURE = "gaze-clusters";

    private final DynamoDbClient dynamo = DynamoDbClient.create();
    private final S3Client s3 = S3Client.create();
    private final String table;
    private final String bucket;

    GazeClusterStorage(String table, String bucket) {
        this.table = table;
        this.bucket = bucket;
    }

    @Override
    public void save(String id, String json) {
        s3.putObject(PutObjectRequest.builder().bucket(bucket)
                        .key(OWNER + "/" + FEATURE + "/" + id + ".json")
                        .contentType("application/json").build(),
                RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));
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
        var result = dynamo.getItem(GetItemRequest.builder().tableName(table)
                .key(Map.of("id", AttributeValue.fromS(id)))
                .consistentRead(true)
                .build());
        if (!result.hasItem() || !result.item().containsKey("resultJson")
                || !OWNER.equals(result.item().get("owner").s())
                || !FEATURE.equals(result.item().get("feature").s())) {
            return null;
        }
        return result.item().get("resultJson").s();
    }
}
