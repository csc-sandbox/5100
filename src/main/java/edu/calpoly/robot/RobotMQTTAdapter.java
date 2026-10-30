package edu.calpoly.robot;

import java.util.function.Consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Subscribes to robot data through MQTT and updates the shared blackboard.
 *
 * @author Paul Motter (PaulMotter)
 * @author Jess A (0x10jalencas)
 * @version 1.1.0 (9/28/2026)
 */
public class RobotMQTTAdapter extends Thread {
    public static final String BASE_TOPIC = "csc5100/robot/";

    private final String topic;
    private final String brokerURI;
    private final Consumer<RobotMessage> onSub;
    private final ObjectMapper mapper;
    private MqttClient client;

    /**
     * Creates the MQTT subscriber for robot data.
     *
     * @param brokerHost MQTT broker host
     * @param brokerPort MQTT broker port
     * @param rbb shared robot blackboard
     */
    RobotMQTTAdapter(
            String brokerHost,
            int brokerPort,
            int robotID,
            RobotBlackBoard rbb) {
        brokerURI = "tcp://" + brokerHost + ":" + brokerPort;
        onSub = rbb::post;
        mapper = new ObjectMapper();
        topic = BASE_TOPIC + robotID;
    }

    /**
     * Connects to the MQTT broker and
     * then subscribes to the robot topic.
     */
    @Override
    public void run() {
        try {
            client = new MqttClient(
                    brokerURI,
                    MqttClient.generateClientId(),
                    new MemoryPersistence());

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(true);

            client.connect(options);
            client.subscribe(topic, (topic, message) ->
                    handlePayload(message.getPayload()));

            System.out.println("Subscribed to " + topic);
        } catch (Exception e) {
            System.err.println(
                    "Unable to start MQTT subscriber: " + e.getMessage());
        }
    }

    /**
     * Parses an MQTT payload and posts it to the blackboard.
     * Invalid payloads are reported and ignored.
     *
     * @param payload raw MQTT message payload
     * @return true if the payload was valid and posted, false otherwise
     */
    boolean handlePayload(byte[] payload) {
        try {
            RobotMessage robotMessage = mapper.readValue(
                    payload,
                    RobotMessage.class);
            onSub.accept(robotMessage);
            return true;
        } catch (Exception e) {
            System.err.println(
                    "Invalid robot message: " + e.getMessage());
            return false;
        }
    }

    /**
     * Returns the topic this adapter subscribes to.
     *
     * @return the MQTT topic
     */
    String getTopic() {
        return topic;
    }
}