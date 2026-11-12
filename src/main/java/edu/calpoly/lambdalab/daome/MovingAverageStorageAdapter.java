package edu.calpoly.lambdalab.daome;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.calpoly.lambdalab.daome.Exceptions.StorageException;
import software.amazon.awssdk.awscore.retry.AwsRetryStrategy;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MovingAverageStorageAdapter implements Storage {
    private static final ObjectMapper mapper = new ObjectMapper();
    private final S3Client s3Client =
            S3Client.builder().overrideConfiguration(o -> o.retryStrategy(AwsRetryStrategy.doNotRetry())).build();
    private final DynamoDbClient dynamoDbClient =
            DynamoDbClient.builder().overrideConfiguration(o -> o.retryStrategy(AwsRetryStrategy.doNotRetry())).build();
    private final String dbTable;
    private final String s3Bucket;
    private final RetryExecutor retryExecutor;

    public MovingAverageStorageAdapter(String dbTable, String s3Bucket) {
        this.dbTable = dbTable;
        this.s3Bucket = s3Bucket;
        this.retryExecutor =
                new RetryExecutor(3, (failure) -> failure instanceof StorageException, RetryExecutor.sleep(90));
    }

    @Override
    public void save(String id, String json) throws StorageException {

        long s3Start = System.nanoTime();
        try {
            retryExecutor.execute(() -> {
                try {
                    String storedJsonString;
                    storedJsonString = mapper.writeValueAsString(mapper.readTree(json));
                    s3Client.putObject(PutObjectRequest
                            .builder()
                            .bucket(s3Bucket)
                            .key("Daome/MovingAverageLambda/" + id + ".json")
                            .contentType("application/json")
                            .build(), RequestBody.fromBytes(storedJsonString.getBytes(StandardCharsets.UTF_8)));
                } catch (RuntimeException | JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            MovingAverageLambdaObservability.log(id, null, "S3", "S3_SAVED", "SUCCESS", s3Start);
        } catch (RuntimeException e) {
            MovingAverageLambdaObservability.log(id, null, "S3", "S3_SAVED", "FAILURE", s3Start);
        }

        long dynamoStart = System.nanoTime();
        try {
            retryExecutor.execute(() -> {
                try {
                    Map<String, AttributeValue> storedMap = Map.of("owner",
                            AttributeValue.fromS(MovingAverageLambda.OWNER),
                            "operation",
                            AttributeValue.fromS(MovingAverageLambda.OPERATION),
                            "id",
                            AttributeValue.fromS(id),
                            "responseJson",
                            AttributeValue.fromS(mapper.writeValueAsString(mapper.readTree(json))));
                    dynamoDbClient.putItem(PutItemRequest.builder().tableName(dbTable).item(storedMap).build());

                } catch (JsonProcessingException e) {
                    throw new RuntimeException(e);
                }
                return null;
            });
            MovingAverageLambdaObservability.log(id, null, "DynamoDB", "DYNAMODB_SAVED", "SUCCESS", dynamoStart);
        } catch (Exception e) {
            MovingAverageLambdaObservability.log(id, null, "DynamoDB", "DYNAMODB_SAVED", "FAILURE", dynamoStart);
        }

    }

    @Override
    public String get(String id) throws StorageException {
        long s3Start = System.nanoTime();
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
        if (item.get("operation") == null || !item.get("operation").s().equals(MovingAverageLambda.OPERATION)) {
            MovingAverageLambdaObservability.log(id, null, "DynamoDB", "DYNAMODB_SAVED", "FAILURE", s3Start);
            return null;
        }

        if (item.get("owner") == null || !item.get("owner").s().equals(MovingAverageLambda.OWNER)) {
            MovingAverageLambdaObservability.log(id, null, "DynamoDB", "DYNAMODB_SAVED", "FAILURE", s3Start);
            throw new StorageException("Missing fields");
        }
        MovingAverageLambdaObservability.log(id, null, "DynamoDB", "DYNAMODB_SAVED", "SUCCESS", s3Start);
        return item.get("responseJson").s();
    }
}
