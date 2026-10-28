
package edu.calpoly.lambdalab.adrian0427;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

final class DominantAffectStorage implements DominantAffectLambda.Storage {

    private final DynamoDbClient dynamo;
    private final S3Client s3;
    private final String table;
    private final String bucket;

    DominantAffectStorage(String table, String bucket) {
        this(DynamoDbClient.create(), S3Client.create(), table, bucket);
    }

    DominantAffectStorage(
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
        String key = "adrian0427/dominant-affect/" + id + ".json";

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType("application/json")
                        .build(),
                RequestBody.fromBytes(
                        json.getBytes(StandardCharsets.UTF_8)));

        dynamo.putItem(
                PutItemRequest.builder()
                        .tableName(table)
                        .item(Map.of(
                                "id", AttributeValue.fromS(id),
                                "owner", AttributeValue.fromS("adrian0427"),
                                "feature", AttributeValue.fromS("dominant-affect"),
                                "resultJson", AttributeValue.fromS(json)))
                        .build());
    }

    @Override
    public String get(String id) {
        var response = dynamo.getItem(
                GetItemRequest.builder()
                        .tableName(table)
                        .key(Map.of("id", AttributeValue.fromS(id)))
                        .consistentRead(true)
                        .build());

        if (!response.hasItem()
                || !response.item().containsKey("resultJson")) {
            return null;
        }

        if (!"adrian0427".equals(response.item().get("owner").s())) {
            return null;
        }

        return response.item().get("resultJson").s();
    }
}
