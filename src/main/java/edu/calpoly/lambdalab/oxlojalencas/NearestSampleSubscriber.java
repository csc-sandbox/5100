package edu.calpoly.lambdalab.oxlojalencas;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;

import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * console app that shows nearest sample events and acknowledges each one.
 *
 * @author Jess A
 * @version October 25, 2026
 */
public class NearestSampleSubscriber {

    static final String BROKER = "tcp://broker.hivemq.com:1883";
    static final String EVENT_TOPIC = "csc5100/events/nearest-sample";

    private static final long TIMEOUT_MILLIS = 10_000;

    private final String broker;
    private final String eventTopic;
    private MqttAsyncClient client;

    public NearestSampleSubscriber() {
        this(BROKER, EVENT_TOPIC);
    }

    NearestSampleSubscriber(String broker, String eventTopic) {
        this.broker = broker;
        this.eventTopic = eventTopic;
    }

    /**
     * connects to the broker and starts listening for events.
     *
     * @throws MqttException if the connection or subscription fails
     */
    public void start() throws MqttException {
        client = new MqttAsyncClient(
                broker,
                MqttAsyncClient.generateClientId(),
                new MemoryPersistence());

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        client.connect(options).waitForCompletion(TIMEOUT_MILLIS);

        client.subscribe(eventTopic, 1, (topic, message) ->
                handle(new String(message.getPayload(), StandardCharsets.UTF_8)))
                .waitForCompletion(TIMEOUT_MILLIS);

        System.out.println("subscribed to " + eventTopic);
    }

    /**
     * disconnects from the broker.
     *
     * @throws MqttException if the disconnect fails
     */
    public void stop() throws MqttException {
        if (client != null && client.isConnected()) {
            client.disconnect(5_000).waitForCompletion(TIMEOUT_MILLIS);
        }
        if (client != null) {
            client.close();
        }
    }

    private void handle(String payload) {
        try {
            NearestSampleEvent event = NearestSampleEvent.parse(payload);
            System.out.println("received " + event.summary());
            sendAck(event.id());
        } catch (IllegalArgumentException exception) {
            System.out.println("ignored invalid event: " + exception.getMessage());
        } catch (MqttException exception) {
            System.out.println("could not send acknowledgment: " + exception.getMessage());
        }
    }

    // publish does not wait, so the broker callback thread is never blocked
    private void sendAck(String id) throws MqttException {
        MqttMessage ack = new MqttMessage(
                NearestSampleAck.jsonFor(id).getBytes(StandardCharsets.UTF_8));
        ack.setQos(1);
        client.publish(NearestSampleAck.topicFor(id), ack);
        System.out.println("sent acknowledgment for " + id);
    }

    /**
     * runs the subscriber until the user presses ctrl+c.
     *
     * @param args not used
     * @throws Exception if the subscriber cannot start
     */
    public static void main(String[] args) throws Exception {
        NearestSampleSubscriber subscriber = new NearestSampleSubscriber();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                subscriber.stop();
            } catch (MqttException exception) {
                System.out.println("could not disconnect: " + exception.getMessage());
            }
        }));

        subscriber.start();
        System.out.println("waiting for events. press ctrl+c to stop.");
        new CountDownLatch(1).await();
    }
}
