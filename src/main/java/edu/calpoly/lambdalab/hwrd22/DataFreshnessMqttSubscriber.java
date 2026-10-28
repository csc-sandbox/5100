package edu.calpoly.lambdalab.hwrd22;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

final class DataFreshnessMqttSubscriber implements AutoCloseable {
    static final String BROKER = "tcp://broker.hivemq.com:1883";
    static final String TOPIC = "csc5100/events/data-freshness";
    static final int QOS = 1;

    @FunctionalInterface
    interface AcknowledgementPublisher {
        void publish(String topic, String json) throws Exception;
    }

    record ProcessedEvent(String id, String displayText, String acknowledgementJson) {}

    private final String broker;
    private final Consumer<String> display;
    private final ObjectMapper mapper = new ObjectMapper();
    private MqttClient client;

    public DataFreshnessMqttSubscriber() {
        this(BROKER, System.out::println);
    }

    DataFreshnessMqttSubscriber(String broker, Consumer<String> display) {
        this.broker = broker;
        this.display = display;
    }

    public synchronized void start() throws Exception {
        if (client != null && client.isConnected()) {
            return;
        }
        client = new MqttClient(
                broker,
                "csc5100-data-freshness-" + UUID.randomUUID(),
                new MemoryPersistence()
        );
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                display.accept("MQTT connection lost: " + safeMessage(cause));
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                try {
                    handleEvent(
                            new String(message.getPayload(), StandardCharsets.UTF_8),
                            DataFreshnessMqttSubscriber.this::publishAcknowledgement
                    );
                } catch (Exception e) {
                    display.accept("Ignored invalid data freshness event: " + safeMessage(e));
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // no update needed
            }
        });

        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        client.connect(options);
        client.subscribe(TOPIC, QOS);
        display.accept("Listening on " + TOPIC + " via " + broker);
    }

    ProcessedEvent handleEvent(String json, AcknowledgementPublisher publisher) throws Exception {
        ProcessedEvent event = parseEvent(json);
        display.accept(event.displayText());
        publisher.publish("csc5100/status/" + event.id(), event.acknowledgementJson());
        return event;
    }

    ProcessedEvent parseEvent(String json) throws Exception {
        JsonNode root = mapper.readTree(json);
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Expected a JSON.");
        }
        String id = root.path("id").asText();
        if (!isUuid(id)) {
            throw new IllegalArgumentException("Invalid UUID.");
        }
        if (!DataFreshnessLambda.OPERATION.equals(root.path("operation").asText())) {
            throw new IllegalArgumentException("Unexpected or unsupported operation");
        }
        JsonNode result = root.path("result");
        if (!result.has("status") || !result.has("age") || !result.get("age").isNumber()) {
            throw new IllegalArgumentException("Missing Data Freshness result.");
        }

        String status = result.get("status").asText();
        long age = result.get("age").asLong();
        String displayText = String.format(
                Locale.ROOT,
                "DataFreshness Event %s: Age = %d, Status = %s",
                id, age, status
        );

        Map<String, Object> acknowledgement = new LinkedHashMap<>();
        acknowledgement.put("id", id);
        acknowledgement.put("operation", DataFreshnessLambda.OPERATION);
        acknowledgement.put("status", "RECEIVED");
        return new ProcessedEvent(id, displayText, mapper.writeValueAsString(acknowledgement));
    }

    private synchronized void publishAcknowledgement(String topic, String json) throws Exception {
        if (client == null || !client.isConnected()) {
            throw new IllegalStateException("MQTT client not connected.");
        }
        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(QOS);
        client.publish(topic, message);
    }

    @Override
    public synchronized void close() {
        if (client == null) {
            return;
        }
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
        } catch (Exception e) {
            display.accept("MQTT disconnect failed: " + safeMessage(e));
        }
        try {
            client.close();
        } catch (Exception e) {
            display.accept("MQTT closure failed: " + safeMessage(e));
        }
        client = null;
    }

    private boolean isUuid(String id) {
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (Exception e) {
            return false;
        }
    }

    private static String safeMessage(Throwable throwable) {
        return throwable == null || throwable.getMessage() == null
                ? "Unknown error."
                : throwable.getMessage();
    }

    public static void main(String[] args) throws Exception {
        String broker = args.length == 0 ? BROKER : args[0];
        CountDownLatch stopped = new CountDownLatch(1);
        DataFreshnessMqttSubscriber subscriber = new DataFreshnessMqttSubscriber(broker, System.out::println);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            subscriber.close();
            stopped.countDown();
        }));

        subscriber.start();
        stopped.await();
    }
}
