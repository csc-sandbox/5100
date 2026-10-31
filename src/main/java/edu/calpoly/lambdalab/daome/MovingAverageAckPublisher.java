package edu.calpoly.lambdalab.daome;

import com.fasterxml.jackson.core.JsonProcessingException;
import edu.calpoly.lambdalab.daome.data.EventStatus;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;

public interface MovingAverageAckPublisher extends MqttCallback {

    void publishAcknowledgement(String id, EventStatus status, String message) throws MqttException,
            JsonProcessingException;
}
