package edu.calpoly.lambdalab.oxlojalencas;

import java.util.Map;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * saves nearest sample results in a dynamodb table with id as the key.
 *
 * @author Jess A
 * @version October 25, 2026
 */
final class NearestSampleStorage implements NearestSampleLambda.Storage {

    private static final String OWNER = "oxlojalencas";
    private static final String FEATURE = "nearest-sample";

    private final DynamoDbClient dynamo;
    private final String table;

    NearestSampleStorage(String table) {
        this(DynamoDbClient.create(), table);
    }

    NearestSampleStorage(DynamoDbClient dynamo, String table) {
        this.dynamo = dynamo;
        this.table = table;
    }

    @Override
    public void save(String id, String json) {
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
