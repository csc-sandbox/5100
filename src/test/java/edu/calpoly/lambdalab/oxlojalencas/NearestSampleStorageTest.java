package edu.calpoly.lambdalab.oxlojalencas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

/**
 * tests for NearestSampleStorage.
 *
 * @author Jess A
 * @version October 25, 2026
 */
class NearestSampleStorageTest {

    private static final String TABLE = "csc5100-results";
    private static final String ID = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
    private static final String JSON = "{\"id\":\"" + ID + "\"}";

    @Test
    void savesItemWithAllFields() {
        StubDynamo dynamo = new StubDynamo();
        NearestSampleStorage storage = new NearestSampleStorage(dynamo, TABLE);

        storage.save(ID, JSON);

        Map<String, AttributeValue> item = dynamo.savedRequest.item();
        assertEquals(TABLE, dynamo.savedRequest.tableName());
        assertEquals(ID, item.get("id").s());
        assertEquals("oxlojalencas", item.get("owner").s());
        assertEquals("nearest-sample", item.get("feature").s());
        assertEquals(JSON, item.get("resultJson").s());
    }

    @Test
    void getUsesConsistentReadAndReturnsJson() {
        StubDynamo dynamo = new StubDynamo();
        dynamo.storedItem = item("oxlojalencas");
        NearestSampleStorage storage = new NearestSampleStorage(dynamo, TABLE);

        String result = storage.get(ID);

        assertEquals(JSON, result);
        assertEquals(TABLE, dynamo.lookupRequest.tableName());
        assertEquals(ID, dynamo.lookupRequest.key().get("id").s());
        assertTrue(dynamo.lookupRequest.consistentRead());
    }

    @Test
    void getReturnsNullWhenItemIsMissing() {
        NearestSampleStorage storage =
                new NearestSampleStorage(new StubDynamo(), TABLE);

        assertNull(storage.get(ID));
    }

    @Test
    void getReturnsNullForDifferentOwner() {
        StubDynamo dynamo = new StubDynamo();
        dynamo.storedItem = item("instructor");
        NearestSampleStorage storage = new NearestSampleStorage(dynamo, TABLE);

        assertNull(storage.get(ID));
    }

    private Map<String, AttributeValue> item(String owner) {
        return Map.of(
                "id", AttributeValue.fromS(ID),
                "owner", AttributeValue.fromS(owner),
                "resultJson", AttributeValue.fromS(JSON));
    }

    /** records requests and returns one stored item, with no network calls. */
    private static class StubDynamo implements DynamoDbClient {
        private PutItemRequest savedRequest;
        private GetItemRequest lookupRequest;
        private Map<String, AttributeValue> storedItem;

        @Override
        public PutItemResponse putItem(PutItemRequest request) {
            savedRequest = request;
            return PutItemResponse.builder().build();
        }

        @Override
        public GetItemResponse getItem(GetItemRequest request) {
            lookupRequest = request;
            if (storedItem == null) {
                return GetItemResponse.builder().build();
            }
            return GetItemResponse.builder().item(storedItem).build();
        }

        @Override
        public String serviceName() {
            return "dynamodb";
        }

        @Override
        public void close() {
        }
    }
}
