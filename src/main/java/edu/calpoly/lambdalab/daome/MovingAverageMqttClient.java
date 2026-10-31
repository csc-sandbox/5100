package edu.calpoly.lambdalab.daome;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;

public class MovingAverageMqttClient implements AutoCloseable {
    public static final String broker = "tcp://broker.hivemq.com:1883";
    public static final String EVENT_TOPIC = "csc5100/events/moving-average";
    private static final String ACK_TOPIC = "csc5100/status/moving-average";
    private static final String clientId = MqttClient.generateClientId();
    private final MqttClient mqttClient;
    private final MovingAverageMqttCallback callback;

    public MovingAverageMqttClient() throws MqttException {
        this.mqttClient = new MqttClient(broker, clientId);
        this.callback = new MovingAverageMqttCallback(ACK_TOPIC, mqttClient);
    }

    public static void main(String[] args) throws Exception {

        var consoleClient = new MovingAverageMqttClient();
        consoleClient.connect();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                consoleClient.close();
            } catch (Exception e) {
                System.err.println("Error on shutdown");
            }
        }));

        Thread.currentThread().join();
    }

    public void connect() throws MqttException {
        var options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);

        mqttClient.setCallback(callback);

        mqttClient.connect(options);
        mqttClient.subscribe(EVENT_TOPIC, 1);

        System.out.println("Connected to Broker");

    }

    @Override
    public void close() throws Exception {
        try {
            if (mqttClient.isConnected()) {
                callback.close();
                mqttClient.disconnect();
            }
        } finally {
            mqttClient.close();
        }
    }
}