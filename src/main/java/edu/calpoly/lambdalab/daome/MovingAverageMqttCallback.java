package edu.calpoly.lambdalab.daome;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.calpoly.lambdalab.daome.data.EventStatus;
import edu.calpoly.lambdalab.daome.data.MovingAverageLambdaEvent;
import edu.calpoly.lambdalab.daome.data.MovingAverageLambdaEventResponse;
import org.eclipse.paho.client.mqttv3.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class MovingAverageMqttCallback implements MqttCallback, MovingAverageAckPublisher, AutoCloseable {
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

        MovingAverageLambdaEvent json;
        try {
            json = mapper.readValue(message.getPayload(), MovingAverageLambdaEvent.class);
            handleEvent(json);
        } catch (IOException | MqttException e) {
            System.err.println(e);
        }

    }


    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    @Override
    public void publishAcknowledgement(String id, EventStatus status, String errorMessage) throws
            JsonProcessingException, RuntimeException {

        var eventResponse =
                new MovingAverageLambdaEventResponse(id, MovingAverageLambda.OPERATION, status, errorMessage);

        var jsonStr = mapper.writeValueAsString(eventResponse);
        executor.execute(() -> {
            try {
                var message = new MqttMessage(jsonStr.getBytes(StandardCharsets.UTF_8));
                message.setQos(1);
                client.publish(ackTopic, message);
                System.out.println("Published Acknowledgment to" + ackTopic);
            } catch (MqttException e) {
                System.err.println("Error While Publishing Acknowledgment");
            }
        });
    }

    public void handleEvent(MovingAverageLambdaEvent json) throws MqttException, JsonProcessingException {
        if (json.result() == null) {
            publishAcknowledgement(json.id(), EventStatus.ERROR, "error while parsing result");
        } else {
            System.out.printf("""
                    Id: %s
                    Samples: %s
                    Window: %s
                    Averages  %s
                    %n""", json.id(), json.result().samples(), json.result().windowSize(), json.result().averages());
            publishAcknowledgement(json.id(), EventStatus.SUCCESS, null);
        }
    }

    @Override
    public void close() throws Exception {
        if (!executor.awaitTermination(500, TimeUnit.MILLISECONDS)) {
            executor.shutdownNow();
        }

    }
}


