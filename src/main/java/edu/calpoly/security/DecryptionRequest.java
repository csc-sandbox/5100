package edu.calpoly.security;

public record DecryptionRequest(String requestId, String encryptedMessage) {
}