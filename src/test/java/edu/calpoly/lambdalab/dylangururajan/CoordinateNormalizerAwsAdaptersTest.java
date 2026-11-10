package edu.calpoly.lambdalab.dylangururajan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

class CoordinateNormalizerAwsAdaptersTest {
  private static final String ID = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";
  private static final String JSON = "{\"id\":\"" + ID + "\"}";

  @Test
  void storageWritesSameResultToS3AndDynamoDbAndCanReadItBack() {
    AtomicReference<PutObjectRequest> s3Request = new AtomicReference<>();
    AtomicReference<byte[]> s3Body = new AtomicReference<>();
    AtomicReference<PutItemRequest> dynamoWrite = new AtomicReference<>();
    AtomicReference<GetItemRequest> dynamoRead = new AtomicReference<>();

    S3Client s3 = proxy(S3Client.class, (proxy, method, arguments) -> {
      if ("putObject".equals(method.getName())) {
        s3Request.set((PutObjectRequest) arguments[0]);
        RequestBody body = (RequestBody) arguments[1];
        s3Body.set(body.contentStreamProvider().newStream().readAllBytes());
        return PutObjectResponse.builder().build();
      }
      return null;
    });
    DynamoDbClient dynamo = proxy(DynamoDbClient.class, (proxy, method, arguments) -> {
      if ("putItem".equals(method.getName())) {
        dynamoWrite.set((PutItemRequest) arguments[0]);
        return PutItemResponse.builder().build();
      }
      if ("getItem".equals(method.getName())) {
        dynamoRead.set((GetItemRequest) arguments[0]);
        return GetItemResponse.builder().item(Map.of(
            "id", AttributeValue.fromS(ID),
            "owner", AttributeValue.fromS(CoordinateNormalizerStorage.OWNER),
            "feature", AttributeValue.fromS(CoordinateNormalizerStorage.FEATURE),
            "resultJson", AttributeValue.fromS(JSON))).build();
      }
      return null;
    });
    CoordinateNormalizerStorage storage =
        new CoordinateNormalizerStorage(dynamo, s3, "results-table", "results-bucket");

    storage.save(ID, JSON);
    String loaded = storage.get(ID);

    assertEquals("results-bucket", s3Request.get().bucket());
    assertEquals(CoordinateNormalizerStorage.S3_PREFIX + ID + ".json", s3Request.get().key());
    assertArrayEquals(JSON.getBytes(StandardCharsets.UTF_8), s3Body.get());
    assertEquals("results-table", dynamoWrite.get().tableName());
    assertEquals(JSON, dynamoWrite.get().item().get("resultJson").s());
    assertEquals("results-table", dynamoRead.get().tableName());
    assertEquals(ID, dynamoRead.get().key().get("id").s());
    assertEquals(JSON, loaded);
  }

  @Test
  void messagingPublishesSameResultToSnsAndSqs() {
    AtomicReference<PublishRequest> snsRequest = new AtomicReference<>();
    AtomicReference<SendMessageRequest> sqsRequest = new AtomicReference<>();
    SnsClient sns = proxy(SnsClient.class, (proxy, method, arguments) -> {
      if ("publish".equals(method.getName())) {
        snsRequest.set((PublishRequest) arguments[0]);
        return PublishResponse.builder().messageId("message-id").build();
      }
      return null;
    });
    SqsClient sqs = proxy(SqsClient.class, (proxy, method, arguments) -> {
      if ("sendMessage".equals(method.getName())) {
        sqsRequest.set((SendMessageRequest) arguments[0]);
        return SendMessageResponse.builder().messageId("message-id").build();
      }
      return null;
    });
    CoordinateNormalizerMessaging messaging = new CoordinateNormalizerMessaging(
        sns, sqs, "arn:aws:sns:us-west-2:123456789012:events", "https://sqs.example/queue");

    messaging.publish(JSON);

    assertEquals("arn:aws:sns:us-west-2:123456789012:events", snsRequest.get().topicArn());
    assertEquals(JSON, snsRequest.get().message());
    assertEquals("https://sqs.example/queue", sqsRequest.get().queueUrl());
    assertEquals(JSON, sqsRequest.get().messageBody());
  }

  @SuppressWarnings("unchecked")
  private <T> T proxy(Class<T> type, InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }
}
