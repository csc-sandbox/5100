package edu.calpoly.lambdalab.ejiswkrosmew;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.net.URI;
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
final public class LidarSafetyStorage implements LidarSafetyLambda.Storage {
    private static final String OWNER = "ejiswkrosmew";

    private final DynamoDbClient dynamo = DynamoDbClient.builder().region(Region.US_WEST_2).build();
    private final S3Client s3 = S3Client.builder().region(Region.US_WEST_2).build();
    private final String table;
    private final String bucket;

    LidarSafetyStorage(String table, String bucket) {
        this.table = table;
        this.bucket = bucket;
    }

    /**
     * Save a lidar-safety query result (JSON) into persistent storage
     * UUID is used as both DynamoDB key and object key for S3 (UUID.json)
     *
     * @param id   UUID as a string
     * @param json The query result as string in JSON format
     */
    @Override
    public void save(String id, String json) {
        // Try to save to S3
        PutObjectRequest PutObjReq = PutObjectRequest.builder()
            .bucket(bucket)
            .key("ejiswkrosmew/lidar-safety/" + id + ".json")
            .contentType("application/json")
            .build();
        s3.putObject(PutObjReq, RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));

        // Try to save to DynamoDB
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

    /**
     * Get a previously stored query result from DynamoDB
     *
     * @param id UUID of the query result desired
     * @return The query result as a String in JSON format. Returns null if failed
     */
    @Override
    public String get(String id) {
        // Try to get the item
        GetItemRequest getItemReq = GetItemRequest.builder()
            .tableName(table)
            .key(Map.of(
                "id", AttributeValue.fromS(id)
            ))
            .consistentRead(true)
            .build();
        GetItemResponse res = dynamo.getItem(getItemReq);

        // If it didn't work, return null
        if (!res.hasItem()) {
            return null;
        }

        // Try to get the json from the item if it exists, otherwise return null
        Map<String, AttributeValue> item = res.item();
        if (!item.containsKey("resultJson") || !item.get("owner").s().equals(OWNER)) {
            return null;
        }
        return item.get("resultJson").s();
    }
}
