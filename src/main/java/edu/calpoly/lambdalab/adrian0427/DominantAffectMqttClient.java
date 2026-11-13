
package edu.calpoly.lambdalab.adrian0427;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

public class DominantAffectMqttClient implements AutoCloseable {

    private static final String DEFAULT_BROKER =
            "tcp://broker.hivemq.com:1883";

    private final MqttClient client;
    private final ExecutorService ackExecutor =
            Executors.newSingleThreadExecutor();

    public DominantAffectMqttClient(String broker) throws MqttException {
        client = new MqttClient(broker, MqttClient.generateClientId());
    }

    public void connect() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);

        client.setCallback(new MqttCallback() {

            @Override
            public void connectionLost(Throwable cause) {
                System.err.println("MQTT connection lost");
            }

            @Override
            public void messageArrived(
                    String topic, MqttMessage message) {

                if (!DominantAffectMqttEvent.EVENT_TOPIC.equals(topic)) {
                    return;
                }

                try {
                    String json = new String(
                            message.getPayload(), StandardCharsets.UTF_8);

                    DominantAffectMqttEvent.Event event =
                            DominantAffectMqttEvent.parse(json);

                    System.out.println("\nDominant affect event received");
                    System.out.println("UUID: " + event.id());
                    System.out.println("Metric: " + event.metric());
                    System.out.println("Value: " + event.value());

                    String ackTopic =
                            DominantAffectMqttEvent.ackTopic(event.id());
                    String ackJson =
                            DominantAffectMqttEvent.ackJson(event);

                    MqttMessage ack = new MqttMessage(
                            ackJson.getBytes(StandardCharsets.UTF_8));
                    ack.setQos(1);

                    ackExecutor.execute(() -> {
                        try {
                            client.publish(ackTopic, ack);
                            System.out.println(
                                    "ACK publish completed: " + ackTopic);
                        } catch (MqttException e) {
                            System.err.println(
                                    "Failed to publish ACK: " + e.getMessage());
                        }
                    });

                } catch (IllegalArgumentException e) {
                    System.err.println("Ignoring invalid MQTT event");
                } catch (RejectedExecutionException e) {
                    System.err.println(
                            "Cannot publish ACK: client is shutting down");
                }
            }

            @Override
            public void deliveryComplete(
                    org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                // No additional processing required.
            }
        });

        client.connect(options);
        client.subscribe(DominantAffectMqttEvent.EVENT_TOPIC, 1);

        System.out.println("Connected to MQTT broker");
        System.out.println(
                "Subscribed to: " + DominantAffectMqttEvent.EVENT_TOPIC);
    }

    @Override
    public void close() throws MqttException {
        ackExecutor.shutdown();

        try {
            if (!ackExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                System.err.println("Timed out waiting for pending ACKs");
                ackExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            ackExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } finally {
            client.close();
        }
    }

    public static void main(String[] args) throws Exception {
        String broker = args.length > 0 ? args[0] : DEFAULT_BROKER;

        try (DominantAffectMqttClient desktop =
                     new DominantAffectMqttClient(broker)) {

            desktop.connect();

            System.out.println("Listening for events. Press Enter to exit.");
            System.in.read();
        }
    }
}
