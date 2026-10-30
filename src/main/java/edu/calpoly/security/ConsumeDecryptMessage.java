package edu.calpoly.security;

import java.io.IOException;
import java.util.Objects;
import java.util.Scanner;

/**
 * Command-line program that consumes the provider's decrypt capability
 * through REST.
 */
public class ConsumeDecryptMessage {

    public static void main(String[] args) {
        EncryptionRestClient client = new EncryptionRestClient();
        Scanner scanner = new Scanner(System.in);

        System.out.println("Paste an encrypted Base64 message to decrypt.");
        System.out.println("Type /quit to quit.");

        String encryptedMessage = "";

        while (!Objects.equals(encryptedMessage, "/quit")) {
            System.out.print("> ");
            encryptedMessage = scanner.nextLine();

            if (Objects.equals(encryptedMessage, "/quit")) {
                break;
            }

            try {
                DecryptionResponse response = client.decrypt(encryptedMessage);

                if (response.success()) {
                    System.out.println("Decrypted: " + response.data());
                } else {
                    System.out.println("Provider error: " + response.error());
                }

            } catch (IllegalArgumentException e) {
                System.out.println("Input error: " + e.getMessage());

            } catch (IOException e) {
                System.out.println(
                        "Could not contact the REST provider: " + e.getMessage()
                );

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("REST request was interrupted.");
                break;
            }
        }

        scanner.close();
    }
}