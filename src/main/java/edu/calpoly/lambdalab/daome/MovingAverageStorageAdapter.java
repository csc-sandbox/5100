package edu.calpoly.lambdalab.daome;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MovingAverageStorageAdapter implements MovingAverageLambda.Storage {
    private final S3Client s3Client = S3Client.create();
    private final DynamoDbClient dynamoDbClient = DynamoDbClient.create();
    private final String dbTable;
    private final String s3Bucket;

    public MovingAverageStorageAdapter(String dbTable, String s3Bucket) {
        this.dbTable = dbTable;
        this.s3Bucket = s3Bucket;
    }

    @Override
    public void save(String id, String json) {
        var storedJson = String.format("""
                {
                "id":  "%s",
                "owner": "%s",
                "operation": "%s",
                "responseJson": "%s"
                }
                """, id, MovingAverageLambda.OWNER, MovingAverageLambda.OPERATION, json);

        Map<String, AttributeValue> storedMap = Map.of("id",
                AttributeValue.fromS(id),
                "owner",
                AttributeValue.fromS(MovingAverageLambda.OWNER),
                "operation",
                AttributeValue.fromS(MovingAverageLambda.OPERATION),
                "responseJson",
                AttributeValue.fromS(json));

        s3Client.putObject(PutObjectRequest
                .builder()
                .bucket(s3Bucket)
                .key("Daome/MovingAverageLambda/" + id + ".json")
                .contentType("application/json")
                .build(), RequestBody.fromBytes(storedJson.getBytes(StandardCharsets.UTF_8)));
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(dbTable).item(storedMap).build());
    }

    @Override
    public String get(String id) {
        var result = dynamoDbClient.getItem(GetItemRequest
                .builder()
                .tableName(dbTable)
                .key(Map.of("id", AttributeValue.fromS(id)))
                .consistentRead(true)
                .build());

        if (!result.hasItem() || result.item().isEmpty()) {
            return null;
        }
        var item = result.item();
        if (!item.get("operation").s().isEmpty() || !item.get("operation").s().equals(MovingAverageLambda.OPERATION)) {
            return null;
        }
        if (!item.get("owner").s().isEmpty() || !item.get("owner").s().equals(MovingAverageLambda.OWNER)) {return null;}

        return item.get("responseJson").s();
    }
}
