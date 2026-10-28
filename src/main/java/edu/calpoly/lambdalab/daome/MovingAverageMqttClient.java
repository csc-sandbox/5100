package edu.calpoly.lambdalab.daome;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

public class MovingAverageMqttClient implements AutoCloseable {
    public static final String broker = "tcp://broker.hivemq.com:1883";
    public static final String EVENT_TOPIC = "csc5100/events/moving-average";
    private static final String clientId = MqttClient.generateClientId();
    private final MqttClient mqttClient;

    public MovingAverageMqttClient() throws MqttException {
        this.mqttClient = new MqttClient(broker, clientId);
    }

    public static void main(String[] args) throws Exception {
        String broker = args.length > 0 ? args[0] : MovingAverageMqttClient.broker;

        try (var desktopClient = new MovingAverageMqttClient();) {
            desktopClient.connect();
        } catch (MqttException ignored) {

        }
   }

    public void connect() throws MqttException {
        var options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);

        mqttClient.setCallback(new MovingAverageMqttCallback());

        mqttClient.connect(options);
        mqttClient.subscribe(EVENT_TOPIC, 1);

        System.out.println("Connected to Broker");

    }

    @Override
    public void close() throws Exception {

        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
        } finally {
            mqttClient.close();
        }
    }
}