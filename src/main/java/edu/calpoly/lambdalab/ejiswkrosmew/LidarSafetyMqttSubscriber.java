package edu.calpoly.lambdalab.ejiswkrosmew;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

public class LidarSafetyMqttSubscriber {
    public static final String DEFAULT_BROKER = "tcp://broker.hivemq.com:1883";
    public static final String EVENT_PREFIX = "csc5100/events/";
    public static final String STATUS_PREFIX = "csc1500/status/";
    public static final String OPERATION = "LidarSafetyLambda";

    private static final ObjectMapper mapper = new ObjectMapper();
    private final String brokerUri;
    private final Consumer<String> displayCallback;
    private MqttClient mqtt;

    LidarSafetyMqttSubscriber() {
        this(DEFAULT_BROKER, System.out::println);
    }

    LidarSafetyMqttSubscriber(String brokerUri, Consumer<String> displayCallback) {
        this.brokerUri = brokerUri;
        this.displayCallback = displayCallback;
    }

    public void start() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);

        mqtt = new MqttClient(brokerUri, "csc5100-lidar-safety-" + UUID.randomUUID());
        mqtt.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                System.err.println("MQTT Connection Lost: " + cause.getMessage());
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String json = new String(message.getPayload(), StandardCharsets.UTF_8);
                JsonNode root = handleMessage(json);
                displayCallback.accept(messageResponse(root));
                publishAck(root);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        mqtt.connect(options);
        mqtt.subscribe(EVENT_PREFIX + OPERATION, 1);
    }

    public JsonNode handleMessage(String json) throws JsonProcessingException {
        JsonNode root = mapper.readTree(json);

        // Ensure root is an object
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("A JSON object was not passed");
        }

        // Ensure id is valid UUID
        String id = root.path("id").asText();
        if (!isUuid(id)) {
            throw new IllegalArgumentException("JSON object did not contain a value UUID");
        }

        // Double check that operation matches
        if (!root.path("operation").asText().equals(OPERATION)) {
            throw new IllegalArgumentException("Operation received was not \"" + OPERATION + "\"");
        }

        // Double check that result exists and is an object
        JsonNode result = root.path("result");
        if (result == null || !result.isObject()) {
            throw new IllegalArgumentException("Result was not found in passed JSON object");
        }

        // Double check that expected result fields exist
        if (result.path("status") == null || !result.path("status").isTextual()) {
            throw new IllegalArgumentException("\"status\" field in results was either not found or was not String");
        }
        if (result.path("minDist") == null || !result.path("minDist").isNumber()) {
            throw new IllegalArgumentException("\"minDist\" field in results was either not found or was not Number");
        }

        // Make sure that status is either WARNING or SAFE
        String status = result.path("status").asText();
        if (!status.equals("WARNING") && !status.equals("SAFE")) {
            throw new IllegalArgumentException("\"status\" field holds invalid value. Should be either \"WARNING\" or \"SAFE\"");
        }

        return root;
    }

    private String messageResponse(JsonNode root) {
        return "Received " + OPERATION + " operation: id=" + root.path("id").asText() + " result=" + root.path("result").toPrettyString();
    }

    private void publishAck(JsonNode root) throws MqttException {
        String id = root.path("id").asText();
        String payload = "{\"id\":\"" + id + "\", \"operation\":\"" + OPERATION + "\", \"status\":\"received\"}";

        MqttMessage msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        msg.setQos(1);
        mqtt.publish(STATUS_PREFIX + id, msg);
    }

    /**
     * Helper function to check if string is a valid UUID
     * Almost directly copied from edu.calpoly.lambdalab.instructor.TemperatureLambda
     *
     * @param id The UUID to test
     * @return True if the UUID is a valid UUID. Otherwise, returns false
     */
    private boolean isUuid(String id) {
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (Exception e) {
            return false;
        }
    }

    public static void main(String[] args) throws MqttException {
        LidarSafetyMqttSubscriber subscriber = new LidarSafetyMqttSubscriber();
        subscriber.start();
        System.out.println("MQTT Subscriber started...");
    }
}
