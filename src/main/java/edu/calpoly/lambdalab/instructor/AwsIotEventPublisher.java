package edu.calpoly.lambdalab.instructor;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.iotdataplane.IotDataPlaneClient;
import software.amazon.awssdk.services.iotdataplane.model.PublishRequest;

/**
 * Publishes A04 event JSON to AWS IoT Core MQTT topics through the AWS IoT Data Plane client using quality of service level 1.
 *
 * @author Javier Gonzalez-Sanchez
 * @version 2.0 (2026-10-29)
 */
public class AwsIotEventPublisher implements AutoCloseable {
  public static final String TOPIC = "csc5100/events/temperature";
  private final IotDataPlaneClient client;

  public AwsIotEventPublisher() {
    this(IotDataPlaneClient.builder()
        .endpointOverride(URI.create("https://" + System.getenv("A04_IOT_ENDPOINT")))
        .build());
  }

  AwsIotEventPublisher(IotDataPlaneClient client) {
    this.client = client;
  }

  public void publish(String topic, String json) {
    client.publish(PublishRequest.builder()
        .topic(topic)
        .qos(1)
        .payload(SdkBytes.fromByteArray(json.getBytes(StandardCharsets.UTF_8)))
        .build());
  }

  @Override
  public void close() {
    client.close();
  }
}
