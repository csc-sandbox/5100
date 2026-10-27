package edu.calpoly.lambdalab.ejiswkrosmew;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
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
 * Storage class to handle storing lidar safety queries in both DynamoDB and S3
 * Acts as the adapter
 * Heavily based on edu.calpoly.lambdalab.instructor.TemperatureStorage
 *
 * @author James Yaguma
 * @version 1.0 (2026-10-27)
 */
public class LidarSafetyStorage implements LidarSafetyLambda.Storage {
    private static final String OWNER = "ejiswkrosmew";

    private final DynamoDbClient dynamo = DynamoDbClient.builder().region(Region.US_WEST_2).build();
    private final S3Client s3 = S3Client.builder().region(Region.US_WEST_2).build();
    private final String table;
    private final String bucket;

    LidarSafetyStorage(String table, String bucket) {
        this.table = table;
        this.bucket = bucket;
    }

    @Override
    public void save(String id, String json) {
        PutObjectRequest PutObjReq = PutObjectRequest.builder()
                .bucket(bucket)
                .key("ejiswkrosmew/lidar-safety" + id + ".json")
                .contentType("application/json")
                .build();
        s3.putObject(PutObjReq, RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));

        PutItemRequest PutItemReq = PutItemRequest.builder()
                .tableName(table)
                .item(Map.of(
                        "id", AttributeValue.fromS(id),
                        "owner", AttributeValue.fromS(OWNER),
                        "feature", AttributeValue.fromS("lidar-safety"),
                        "resultJson", AttributeValue.fromS(json))
                )
                .build();
        dynamo.putItem(PutItemReq);
    }

    @Override
    public String get(String id) {
        GetItemRequest getItemReq = GetItemRequest.builder()
                .tableName(table)
                .key(Map.of(
                        "id", AttributeValue.fromS(id)
                ))
                .consistentRead(true)
                .build();
        GetItemResponse res = dynamo.getItem(getItemReq);
        if (!res.hasItem()) {
            return null;
        }
        Map<String, AttributeValue> item = res.item();
        if (!item.containsKey("resultJson") || !item.get("owner").s().equals(OWNER)) {
            return null;
        }
        return item.get("resultJson").s();
    }
}
