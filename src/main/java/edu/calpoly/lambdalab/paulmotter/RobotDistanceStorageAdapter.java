package edu.calpoly.lambdalab.paulmotter;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class RobotDistanceStorageAdapter implements RobotDistanceLambda.StorageAdapter {

    private final S3Client s3Client;
    private final DynamoDbClient dynamoDbClient;
    private final String table;
    private final String bucket;

    /**
     * Production constructor: creates default AWS clients.
     * @param dbTable DynamoDB table name
     * @param s3Bucket S3 bucket name
     */
    RobotDistanceStorageAdapter(String dbTable, String s3Bucket){
        this(dbTable, s3Bucket, S3Client.create(), DynamoDbClient.create());
    }

    /**
     * Constructor that accepts its AWS clients, so tests can supply fakes.
     * @param dbTable DynamoDB table name
     * @param s3Bucket S3 bucket name
     * @param s3Client S3 client to use
     * @param dynamoDbClient DynamoDB client to use
     */
    RobotDistanceStorageAdapter(String dbTable, String s3Bucket,
                                S3Client s3Client, DynamoDbClient dynamoDbClient){
        this.table = dbTable;
        this.bucket = s3Bucket;
        this.s3Client = s3Client;
        this.dynamoDbClient = dynamoDbClient;
    }

    @Override
    public void save(RobotDistanceLambdaData data, String json) {
        // Store object first; a failed DynamoDB write can leave an orphan S3 object.
        // Reconciliation/compensation is a later enhancement, not a transaction.
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket)
            .key("PaulMotter/DistanceLambda/" + data.id + ".json")
            .contentType("application/json").build(),
            RequestBody.fromBytes(json.getBytes(StandardCharsets.UTF_8)));
        
        dynamoDbClient.putItem(PutItemRequest.builder().tableName(table)
            .item(Map.of(
                "id", AttributeValue.fromS(data.id),
                "operation", AttributeValue.fromS(RobotDistanceLambda.OPERATION_NAME),
                "owner", AttributeValue.fromS(RobotDistanceLambda.OWNER_NAME),
                "resultJson", AttributeValue.fromS(json)
            )).build()
        );
    }
    
    @Override
    public String get(String id) {
        var result = dynamoDbClient.getItem(GetItemRequest.builder().tableName(table)
            .key(Map.of("id", AttributeValue.fromS(id))).consistentRead(true).build());
    
        // Checks that validation fields exist.
        if (!result.hasItem() || !result.item().containsKey("operation") || !result.item().containsKey("owner")) return null;
        // Validate the record's operaiton.
        if (!result.item().get("operation").s().equals(RobotDistanceLambda.OPERATION_NAME)) return null;
        // Validate the record's owner.
        if (!result.item().get("owner").s().equals(RobotDistanceLambda.OWNER_NAME)) return null;
    
        return result.item().get("resultJson").s();
    }
    
}