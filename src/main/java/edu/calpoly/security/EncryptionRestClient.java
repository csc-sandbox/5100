package edu.calpoly.security;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

/**
 * REST consumer for the encryption provider's decrypt operation.
 */
public class EncryptionRestClient {

    private static final String URL =
            "http://localhost:8080/encryption/decrypt";

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Sends encrypted Base64 text to the provider and returns its response.
     *
     * @param encryptedMessage Base64 encrypted text to decrypt
     * @return the provider's decrypt response
     * @throws IOException if communication or response parsing fails
     * @throws InterruptedException if the request is interrupted
     */
    public DecryptionResponse decrypt(String encryptedMessage)
            throws IOException, InterruptedException {

        if (encryptedMessage == null || encryptedMessage.isBlank()) {
            throw new IllegalArgumentException(
                    "Encrypted message cannot be empty.");
        }

        DecryptionRequest decryptRequest = new DecryptionRequest(
                UUID.randomUUID().toString(),
                encryptedMessage
        );

        String json = mapper.writeValueAsString(decryptRequest);

        HttpRequest request = HttpRequest.newBuilder(URI.create(URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "REST decrypt request failed: HTTP "
                            + response.statusCode()
                            + " - " + response.body()
            );
        }

        return mapper.readValue(response.body(), DecryptionResponse.class);
    }
}