package edu.calpoly.lambdalab.paulmotter;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.function.Consumer;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Console application that subscribes to csc5100/events/robot-distance,
 * displays each RobotDistanceLambda event, and publishes an ACK (QoS 1) to
 * csc5100/status/<UUID>.
 *
 * Run: {@code java -cp target/csc5100-1.0.jar edu.calpoly.lambdalab.paulmotter.RobotDistanceDesktopClient [brokerUri]}
 * Default broker: tcp://broker.hivemq.com:1883 (public; synthetic data only).
 *
 * @author Paul Motter (PaulMotter)
 * @version 1.0 (10/21/2026)
 */
public class RobotDistanceDesktopClient implements AutoCloseable {
    public static final String DEFAULT_BROKER = "tcp://broker.hivemq.com:1883";
    static final int QOS = 1;

    private final MqttClient client;
    private final RobotDistanceEventHandler handler;
    private final Consumer<String> display;

    /**
     * Creates the client; does not connect until {@link #start()}.
     *
     * @param brokerUri MQTT broker URI, e.g. tcp://broker.hivemq.com:1883
     * @param display receives output lines
     * @throws MqttException if the URI is invalid
     */
    public RobotDistanceDesktopClient(String brokerUri, Consumer<String> display) throws MqttException {
        this.display = display;
        String clientId = "csc5100-paulmotter-" + UUID.randomUUID();
        client = new MqttClient(brokerUri, clientId, new MemoryPersistence());
        handler = new RobotDistanceEventHandler(this::publish, display, clientId, Clock.systemUTC());
    }

    /**
     * Wraps an ACK payload in a QoS 1 MQTT message.
     *
     * @param json ACK JSON
     * @return the message to publish
     */
    static MqttMessage ackMessage(String json) {
        MqttMessage message = new MqttMessage(json.getBytes(StandardCharsets.UTF_8));
        message.setQos(QOS);
        return message;
    }

    /**
     * Connects and subscribes. Returns once the subscription is active.
     *
     * @throws MqttException if connecting or subscribing fails
     */
    public void start() throws MqttException {
        client.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {
                // Clean sessions drop subscriptions, so restore them after an automatic reconnect.
                if (reconnect) {
                    display.accept("[mqtt] reconnected to " + serverURI);
                    subscribeQuietly();
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                display.accept("[mqtt] connection lost: " + cause.getMessage() + " (reconnecting)");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                handler.handle(new String(message.getPayload(), StandardCharsets.UTF_8));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) { }
        });

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        client.connect(options);
        client.subscribe(RobotDistanceEventHandler.EVENT_TOPIC, QOS);
        display.accept("[mqtt] connected to " + client.getServerURI()
            + ", subscribed to " + RobotDistanceEventHandler.EVENT_TOPIC);
    }

    private void subscribeQuietly() {
        try {
            client.subscribe(RobotDistanceEventHandler.EVENT_TOPIC, QOS);
        } catch (MqttException e) {
            display.accept("[mqtt] resubscribe failed: " + e.getMessage());
        }
    }

    private void publish(String topic, String json) {
        try {
            client.publish(topic, ackMessage(json));
        } catch (MqttException e) {
            throw new IllegalStateException("MQTT publish failed", e);
        }
    }

    /**
     * Disconnects and releases the client.
     */
    @Override
    public void close() {
        try {
            if (client.isConnected()) client.disconnect();
        } catch (MqttException ignored) {
            // Shutting down regardless.
        }
        try {
            client.close();
        } catch (MqttException ignored) { }
    }

    /**
     * Runs until interrupted (Ctrl+C).
     *
     * @param args optional broker URI
     * @throws Exception if the client cannot start
     */
    public static void main(String[] args) throws Exception {
        String broker = args.length > 0 ? args[0] : DEFAULT_BROKER;
        RobotDistanceDesktopClient app = new RobotDistanceDesktopClient(broker, System.out::println);
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            app.close();
            stopped.countDown();
        }));
        app.start();
        System.out.println("Waiting for events. Press Ctrl+C to exit.");
        stopped.await();
    }
}
