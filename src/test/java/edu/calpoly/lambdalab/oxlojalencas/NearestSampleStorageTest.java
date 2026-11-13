package edu.calpoly.lambdalab.oxlojalencas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

/**
 * tests for NearestSampleStorage.
 *
 * @author Jess A
 * @version October 25, 2026
 */
class NearestSampleStorageTest {

    private static final String TABLE = "csc5100-results";
    private static final String BUCKET = "csc5100-results";
    private static final String ID = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
    private static final String JSON = "{\"id\":\"" + ID + "\"}";

    @Test
    void savesItemWithAllFields() {
        StubDynamo dynamo = new StubDynamo();
        NearestSampleStorage storage = storageFor(dynamo, new StubS3());

        storage.save(ID, JSON);

        Map<String, AttributeValue> item = dynamo.savedRequest.item();
        assertEquals(TABLE, dynamo.savedRequest.tableName());
        assertEquals(ID, item.get("id").s());
        assertEquals("oxlojalencas", item.get("owner").s());
        assertEquals("nearest-sample", item.get("feature").s());
        assertEquals(JSON, item.get("resultJson").s());
    }

    @Test
    void savesSameJsonToS3() {
        StubS3 s3 = new StubS3();
        NearestSampleStorage storage = storageFor(new StubDynamo(), s3);

        storage.save(ID, JSON);

        assertEquals(BUCKET, s3.savedRequest.bucket());
        assertEquals("oxlojalencas/nearest-sample/" + ID + ".json",
                s3.savedRequest.key());
        assertEquals("application/json", s3.savedRequest.contentType());
        assertEquals(JSON, s3.savedBody);
    }

    @Test
    void s3FailureStopsDynamoWrite() {
        StubDynamo dynamo = new StubDynamo();
        StubS3 s3 = new StubS3();
        s3.fails = true;
        NearestSampleStorage storage = storageFor(dynamo, s3);

        assertThrows(IllegalStateException.class,
                () -> storage.save(ID, JSON));

        assertNull(dynamo.savedRequest);
    }

    @Test
    void dynamoFailureReachesCallerAfterS3Write() {
        StubDynamo dynamo = new StubDynamo();
        dynamo.fails = true;
        StubS3 s3 = new StubS3();
        NearestSampleStorage storage = storageFor(dynamo, s3);

        assertThrows(IllegalStateException.class,
                () -> storage.save(ID, JSON));

        assertNotNull(s3.savedRequest);
    }

    @Test
    void getUsesConsistentReadAndReturnsJson() {
        StubDynamo dynamo = new StubDynamo();
        dynamo.storedItem = item("oxlojalencas");
        NearestSampleStorage storage = storageFor(dynamo, new StubS3());

        String result = storage.get(ID);

        assertEquals(JSON, result);
        assertEquals(TABLE, dynamo.lookupRequest.tableName());
        assertEquals(ID, dynamo.lookupRequest.key().get("id").s());
        assertTrue(dynamo.lookupRequest.consistentRead());
    }

    @Test
    void getReturnsNullWhenItemIsMissing() {
        NearestSampleStorage storage =
                storageFor(new StubDynamo(), new StubS3());

        assertNull(storage.get(ID));
    }

    @Test
    void getReturnsNullForDifferentOwner() {
        StubDynamo dynamo = new StubDynamo();
        dynamo.storedItem = item("instructor");
        NearestSampleStorage storage = storageFor(dynamo, new StubS3());

        assertNull(storage.get(ID));
    }

    private NearestSampleStorage storageFor(StubDynamo dynamo, StubS3 s3) {
        return new NearestSampleStorage(dynamo, s3, TABLE, BUCKET);
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
        private boolean fails;

        @Override
        public PutItemResponse putItem(PutItemRequest request) {
            if (fails) {
                throw new IllegalStateException("dynamodb unavailable");
            }
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

    /** records the saved object instead of calling s3. */
    private static class StubS3 implements S3Client {
        private PutObjectRequest savedRequest;
        private String savedBody;
        private boolean fails;

        @Override
        public PutObjectResponse putObject(
                PutObjectRequest request,
                RequestBody body) {
            if (fails) {
                throw new IllegalStateException("s3 unavailable");
            }
            savedRequest = request;
            try {
                savedBody = new String(
                        body.contentStreamProvider().newStream().readAllBytes(),
                        StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new IllegalStateException(exception);
            }
            return PutObjectResponse.builder().build();
        }

        @Override
        public String serviceName() {
            return "s3";
        }

        @Override
        public void close() {
        }
    }
}
