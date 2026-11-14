package edu.calpoly.lambdalab.daome;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.calpoly.lambdalab.daome.data.EventStatus;
import edu.calpoly.lambdalab.daome.data.MovingAverageLambdaEventResponse;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static edu.calpoly.lambdalab.daome.MovingAverageMqttCallback.OPERATION;

public class AcknowledgementAdapter implements AckPublisher, AutoCloseable {
    private final ObjectMapper mapper = new ObjectMapper();
    public static final String ACK_TOPIC = "csc5100/status/";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MqttClient client;

    public AcknowledgementAdapter(MqttClient client) {
        this.client = client;
    }

    @Override
    public void publish(String id, EventStatus status, String errorMessage) {

        var eventResponse = new MovingAverageLambdaEventResponse(UUID.fromString(id), OPERATION, status, errorMessage);

        try {
            String jsonStr = mapper.writeValueAsString(eventResponse);
            executor.execute(() -> {
                try {
                    var message = new MqttMessage(jsonStr.getBytes(StandardCharsets.UTF_8));
                    message.setQos(1);
                    client.publish(ACK_TOPIC + id, message);
                    System.out.println("Published Acknowledgment to" + ACK_TOPIC + id);
                } catch (MqttException e) {
                    System.err.println("Error While Publishing Acknowledgment");
                }
            });
        } catch (JsonProcessingException e) {
            System.err.println("Error while publishing Acknowledgement");
        }

    }

    @Override
    public void close() {
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
