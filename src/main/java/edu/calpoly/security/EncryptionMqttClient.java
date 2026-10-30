package edu.calpoly.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * MQTT consumer for the provider's decrypt operation.
 *
 * <p>Requests are published to {@value #DECRYPT_REQUEST_TOPIC}; responses are
 * received from {@value #DECRYPT_RESPONSE_TOPIC}. Each request includes a
 * unique request ID so that the response can be matched safely.</p>
 */
public class EncryptionMqttClient implements AutoCloseable {

    /** Public MQTT broker used by the course example. */
    public static final String BROKER = "tcp://broker.hivemq.com:1883";
    /** Topic to which decrypt requests are published. */
    public static final String DECRYPT_REQUEST_TOPIC =
            "csc5100/encryption/decrypt/request";
    /** Topic on which decrypt responses are received. */
    public static final String DECRYPT_RESPONSE_TOPIC =
            "csc5100/encryption/decrypt/response";

    private static final int RESPONSE_TIMEOUT_SECONDS = 10;

    private final ObjectMapper mapper = new ObjectMapper();
    private final MqttClient client;
    private final ConcurrentHashMap<String, CompletableFuture<DecryptionResponse>>
            pendingResponses = new ConcurrentHashMap<>();

    /**
     * Creates an MQTT client, connects it, and subscribes to decrypt responses.
     *
     * @throws MqttException if the broker connection or subscription fails
     */
    public EncryptionMqttClient() throws MqttException {
        client = new MqttClient(BROKER, MqttClient.generateClientId());

        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                failPendingRequests("MQTT connection lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                handleResponse(message);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // No action is required after a request is delivered.
            }
        });

        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        client.connect(options);
        client.subscribe(DECRYPT_RESPONSE_TOPIC);
    }

    /**
     * Requests that the provider decrypt an encrypted Base64 message.
     *
     * @param encryptedMessage Base64-encoded encrypted message
     * @return the provider's matching response
     * @throws IllegalArgumentException if the input is blank
     * @throws IOException if the response is malformed or times out
     * @throws MqttException if MQTT publishing fails
     * @throws InterruptedException if waiting for the response is interrupted
     */
    public DecryptionResponse decrypt(String encryptedMessage)
            throws IOException, MqttException, InterruptedException {
        if (encryptedMessage == null || encryptedMessage.isBlank()) {
            throw new IllegalArgumentException("Encrypted message cannot be empty.");
        }

        String requestId = UUID.randomUUID().toString();
        CompletableFuture<DecryptionResponse> responseFuture = new CompletableFuture<>();
        pendingResponses.put(requestId, responseFuture);

        try {
            DecryptionRequest request = new DecryptionRequest(requestId, encryptedMessage);