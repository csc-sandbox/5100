package edu.calpoly.lambdalab.hwrd22;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RetryExecutorTest {
    private static RetryExecutor threeAttempts(AtomicInteger delays) {
        return new RetryExecutor(3,
                failure -> failure instanceof IllegalStateException,
                delays::incrementAndGet);
    }

    @Test
    void transientFailureSucceedsOnThirdAttempt() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();
        Storage fakeStorage = new Storage() {
            String id;
            String json;
            @Override
            public void save(String saveId, String saveJson) {
                if (attempts.incrementAndGet() < 3) {
                    throw new IllegalStateException("Simulated temporary outage");
                }
                else {
                    id = saveId;
                    json = saveJson;
                }
            }

            @Override
            public String get(String getId) {
                return getId.equals(id) ? json : null;
            }
        };
        Storage storage = new RetryStorage(fakeStorage, threeAttempts(delays));

        storage.save("test", "{\"id\":\"test\"}");

        assertEquals(3, attempts.get());
        assertEquals(2, delays.get());

        assertEquals("{\"id\":\"test\"}", storage.get("test"));  // Proves the item saved
    }

    @Test
    void permanentFailureStopsAfterThreeAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();
        IllegalStateException outage = new IllegalStateException("Still unavailable");
        Storage fakeStorage = new Storage() {
            @Override
            public void save(String id, String json) {
                attempts.incrementAndGet();
                throw outage;
            }

            @Override
            public String get(String id) {
                return "";
            }
        };
        Storage storage = new RetryStorage(fakeStorage, threeAttempts(delays));

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> storage.save("test", "{\"id\":\"test\"}"));

        assertSame(outage, thrown);
        assertEquals(3, attempts.get());
        assertEquals(2, delays.get());
    }

    @Test
    void nonRetryableFailureStopsImmediately() {
        AtomicInteger attempts = new AtomicInteger();
        AtomicInteger delays = new AtomicInteger();
        Storage fakeStorage = new Storage() {
            @Override
            public void save(String id, String json) {
                attempts.incrementAndGet();
                throw new IllegalArgumentException("Invalid input");
            }

            @Override
            public String get(String id) {
                return "";
            }
        };
        Storage storage = new RetryStorage(fakeStorage, threeAttempts(delays));

        assertThrows(IllegalArgumentException.class, () -> storage.save("", "{}"));
        assertEquals(1, attempts.get());
        assertEquals(0, delays.get());
    }

    @Test
    void retriesS3PutObjectTwiceThenSucceedsOnThirdCall() {
        AtomicInteger putObjectCalls = new AtomicInteger();
        SdkClientException first = SdkClientException.create("temporary failure 1");
        SdkClientException second = SdkClientException.create("temporary failure 2");

        S3Client s3 = fakeS3(putObjectCalls, first, second);
        DataFreshnessStorage storage = storageWith(s3);

        storage.save("event-1", "{\"status\":\"ok\"}");

        assertEquals(3, putObjectCalls.get());
    }

    @Test
    void stopsAfterThreeS3PutObjectFailuresAndPropagatesFinalError() {
        AtomicInteger putObjectCalls = new AtomicInteger();
        SdkClientException first = SdkClientException.create("temporary failure 1");
        SdkClientException second = SdkClientException.create("temporary failure 2");
        SdkClientException last = SdkClientException.create("temporary failure 3");

        S3Client s3 = fakeS3(putObjectCalls, first, second, last);
        DataFreshnessStorage storage = storageWith(s3);

        SdkClientException thrown = assertThrows(
                SdkClientException.class,
                () -> storage.save("event-1", "{\"status\":\"ok\"}")
        );

        assertSame(last, thrown);
        assertEquals(3, putObjectCalls.get());
    }

    private static DataFreshnessStorage storageWith(S3Client s3) {
        RetryExecutor retryExecutor = new RetryExecutor(
                3,
                failure -> failure instanceof SdkClientException,
                () -> {}
        );

        return new DataFreshnessStorage(
                fakeDynamoDb(),
                s3,
                "test-table",
                "test-bucket",
                retryExecutor
        );
    }

    // Class that fakes an S3 client
    private static S3Client fakeS3(
            AtomicInteger putObjectCalls,
            RuntimeException... failures
    ) {
        return (S3Client) Proxy.newProxyInstance(
                S3Client.class.getClassLoader(),
                new Class<?>[]{S3Client.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("putObject")) {
                        int call = putObjectCalls.incrementAndGet();
                        if (call <= failures.length) {
                            throw failures[call - 1];
                        }
                        return PutObjectResponse.builder().build();
                    }
                    if (method.getName().equals("close")) return null;
                    if (method.getName().equals("toString")) return "FakeS3Client";
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) return proxy == args[0];

                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    // Class that fakes a successful DynamoDB client
    private static DynamoDbClient fakeDynamoDb() {
        return (DynamoDbClient) Proxy.newProxyInstance(
                DynamoDbClient.class.getClassLoader(),
                new Class<?>[]{DynamoDbClient.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("putItem")) {
                        return PutItemResponse.builder().build();
                    }
                    if (method.getName().equals("close")) return null;
                    if (method.getName().equals("toString")) return "FakeDynamoDbClient";
                    if (method.getName().equals("hashCode")) {
                        return System.identityHashCode(proxy);
                    }
                    if (method.getName().equals("equals")) return proxy == args[0];

                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
