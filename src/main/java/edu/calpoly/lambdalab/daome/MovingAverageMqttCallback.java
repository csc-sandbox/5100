package edu.calpoly.lambdalab.daome;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MovingAverageMqttCallback implements MqttCallback, MovingAverageAckPublisher {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MqttClient client;
    private final String ackTopic;

    public MovingAverageMqttCallback(String ackTopic, MqttClient client) {
        this.ackTopic = ackTopic;
        this.client = client;
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println(cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        if (!topic.equals(MovingAverageMqttClient.EVENT_TOPIC)) {return;}

        MovingAverageData json;
        try {
            // Jackson throws error if non-valid json schema
            json = mapper.readValue(message.getPayload(), MovingAverageData.class);
        } catch (IOException e) {
            return;
        }

        System.out.printf("""
                Id: %s
                Samples: %s
                Window: %s
                Averages  %s
                %n""", json.id(), json.samples(), json.windowSize(), json.averages());

        try {
            Map<String, Object> ackJson = mapper.convertValue(json, new TypeReference<>() {});
            ackJson.put("status", "ACK");
            var jsonStr = mapper.writeValueAsString(ackJson);

            executor.execute(() -> {
                try {
                    publishAcknowledgement(jsonStr);
                    System.out.println("Published Acknowledgment to" + ackTopic);
                } catch (MqttException e) {
                    System.err.println("Error While Publishing Acknowledgment");
                }
            });
        } catch (JsonProcessingException | RuntimeException ignored) {
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @Override
    public void publishAcknowledgement(String json) throws MqttException {
        var message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        client.publish(ackTopic, message);
    }
}
