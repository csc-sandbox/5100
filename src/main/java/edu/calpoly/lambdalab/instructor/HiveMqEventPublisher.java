package edu.calpoly.lambdalab.instructor;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Publishes A04 event JSON to the public HiveMQ MQTT broker using a short-lived MQTT client and quality of service level 1.
 *
 * @author Javier Gonzalez-Sanchez
 * @version 2.0 (2026-10-29)
 */
public class HiveMqEventPublisher {
  public static final String TOPIC = AwsIotEventPublisher.TOPIC;
  public static final String BROKER = "tcp://broker.hivemq.com:1883";

  public void publish(String topic, String json) {
    MqttClient client = null;
    try {
      client = new MqttClient(BROKER, "csc5100-a04-" + UUID.randomUUID(), new MemoryPersistence());
      MqttConnectOptions options = new MqttConnectOptions();
      options.setConnectionTimeout(5);
      options.setAutomaticReconnect(false);
      options.setCleanSession(true);
      client.connect(options);
      MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
      message.setQos(1);
      client.publish(topic, message);
    } catch (Exception e) {
      throw new IllegalStateException("HiveMQ publish failed", e);
    } finally {
      if (client != null) {
        try {
          if (client.isConnected()) client.disconnect();
        } catch (Exception ignored) {
          // Connection is being discarded after this invocation.
        }
        try { client.close(); } catch (Exception ignored) { }
      }
    }
  }
}
