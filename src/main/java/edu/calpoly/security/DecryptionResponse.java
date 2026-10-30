package edu.calpoly.security;

/**
 * JSON response returned by the encryption provider after a decrypt request.
 */
public record DecryptionResponse(
        String requestId,
        boolean success,
        String data,
        String error
) {
}