package edu.calpoly.lambdalab.daome;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;

public class MovingAverageMqttCallback implements MqttCallback {
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println(cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        if (!topic.equals(MovingAverageMqttClient.EVENT_TOPIC))
            return;

        MovingAverageData json;
        // Jackson throws error if non-valid json schema
        try {
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
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {

    }

}
