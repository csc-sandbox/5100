package edu.calpoly.lambdalab.oxlojalencas;

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
 * saves nearest sample results in s3 and in a dynamodb table keyed by id.
 *
 * @author Jess A
 * @version October 25, 2026
 */
final class NearestSampleStorage implements NearestSampleLambda.Storage {

    private static final String OWNER = "oxlojalencas";
    private static final String FEATURE = "nearest-sample";

    private final DynamoDbClient dynamo;
    private final S3Client s3;
    private final String table;
    private final String bucket;

    NearestSampleStorage(String table, String bucket) {
        this(DynamoDbClient.create(), S3Client.create(), table, bucket);
    }

    NearestSampleStorage(
            DynamoDbClient dynamo,
            S3Client s3,
            String table,
            String bucket) {
        this.dynamo = dynamo;
        this.s3 = s3;
        this.table = table;
        this.bucket = bucket;
    }

    @Override
    public void save(String id, String json) {
        // s3 goes first, so a failed write never leaves a dynamodb item behind
        s3.putObject(PutObjectRequest.builder()
                .bucket(bucket)
                .key(OWNER + "/" + FEATURE + "/" + id + ".json")
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

        // the table is shared, so ignore items saved by other students
        AttributeValue owner = response.item().get("owner");
        if (owner == null || !OWNER.equals(owner.s())) {
            return null;
        }

        AttributeValue resultJson = response.item().get("resultJson");
        return resultJson == null ? null : resultJson.s();
    }
}
