package edu.calpoly.lambdalab.jjuangon03;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GazeClusterMessagingTest {

    @Test
    void publishesTheSameJsonIndependentlyToSnsAndSqs() {
        List<String> sns = new ArrayList<>();
        List<String> sqs = new ArrayList<>();
        GazeClusterMessaging messaging = new GazeClusterMessaging(sns::add, sqs::add);
        String json = "{\"id\":\"correlation-id\",\"result\":{}}";

        messaging.publish(json);

        assertEquals(List.of(json), sns);
        assertEquals(List.of(json), sqs);
    }

    @Test
    void attemptsSqsEvenWhenSnsPublishingFails() {
        List<String> sqs = new ArrayList<>();
        GazeClusterMessaging messaging = new GazeClusterMessaging(
                json -> {
                    throw new IllegalStateException("SNS unavailable");
                },
                sqs::add);
        String json = "{\"id\":\"correlation-id\",\"result\":{}}";

        assertThrows(IllegalStateException.class, () -> messaging.publish(json));
        assertEquals(List.of(json), sqs);
    }
}
