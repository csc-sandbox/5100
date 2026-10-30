package edu.calpoly.robot;

import java.nio.charset.StandardCharsets;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * RobotMQTTProvider publishes messages to the robot topic for a specific robotId.
 * 
 * @author Paul Motter (PaulMotter)
 * @version 1.0.0 (9/29/2026)
 */
public class RobotMQTTProvider{
    
    private final MqttClient client;
    private final int robotId;
    private final String serverURI;

    /**
     * Creates a client to send messages from.
     * @param brokerHost host for the client.
     * @param brokerPort port for the client.
     * @param robotId specific robot topic to send messages to.
     * @throws MqttException
     */
    public RobotMQTTProvider(String brokerHost, int brokerPort, int robotId) throws MqttException{
        this.robotId = robotId;
        serverURI = "tcp://" + brokerHost + ":" + brokerPort;
        client = new MqttClient(
            serverURI,
            MqttClient.generateClientId(),
            new MemoryPersistence()
        );

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        client.connect(options);  
    }

    /**
     * Allows the publishing of a RobotMessage object.
     * @param payload
     * @throws JsonProcessingException
     * @throws Exception
     */
    public void publish(RobotMessage payload) throws JsonProcessingException, Exception {
        ObjectMapper mapper = new ObjectMapper();
        publish(mapper.writeValueAsString(payload));
    }

    /**
     * Createes a message and sends it to the topic defined for the provider.
     * @param payload The string message to be sent.
     * @throws Exception
     */
    public void publish(String payload) throws Exception {
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(0);

        client.publish(RobotMQTTAdapter.BASE_TOPIC+robotId, message);
        System.out.println("Published to "
                + RobotMQTTAdapter.BASE_TOPIC+robotId
                + ": "
                + payload);
    }
}
