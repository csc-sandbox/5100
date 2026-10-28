package edu.calpoly.lambdalab.daome;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttException;

public interface MovingAverageAckPublisher extends MqttCallback {
    void publishAcknowledgement(String json) throws MqttException;

}
