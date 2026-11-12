package edu.calpoly.lambdalab.daome;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.calpoly.lambdalab.daome.data.EventStatus;
import edu.calpoly.lambdalab.daome.data.MovingAverageLambdaEvent;
import org.eclipse.paho.client.mqttv3.*;

import java.io.IOException;
import java.util.UUID;


public class MovingAverageMqttCallback implements MqttCallback, AutoCloseable {
    public static final String OPERATION = "MovingAverageLambda";
    private static final ObjectMapper mapper = new ObjectMapper();
    private final AckPublisher ackPublisher;

    public MovingAverageMqttCallback(AckPublisher ackPublisher) {
        this.ackPublisher = ackPublisher;
    }

    @Override
    public void connectionLost(Throwable cause) {
        System.out.println(cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        if (!topic.equals(MovingAverageMqttClient.EVENT_TOPIC)) {return;}

        MovingAverageLambdaEvent json;
        try {
            json = mapper.readValue(message.getPayload(), MovingAverageLambdaEvent.class);
            handleEvent(json);
        } catch (IOException | MqttException | IllegalArgumentException e) {
            ackPublisher.publish(String.valueOf(new UUID(0L, 0L)), EventStatus.ERROR, "");
        }

    }


    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }


    public void handleEvent(MovingAverageLambdaEvent json) throws MqttException, JsonProcessingException {
        if (json.result() == null) {
            ackPublisher.publish(String.valueOf(json.id()), EventStatus.ERROR, "error while parsing result");
        } else {
            System.out.printf("""
                    Id: %s
                    Samples: %s
                    Window: %s
                    Averages:  %s
                    %n""", json.id(), json.result().samples(), json.result().windowSize(), json.result().averages());
            ackPublisher.publish(String.valueOf(json.id()), EventStatus.SUCCESS, null);
        }
    }

    @Override
    public void close() throws Exception {
        ackPublisher.close();
    }
}


