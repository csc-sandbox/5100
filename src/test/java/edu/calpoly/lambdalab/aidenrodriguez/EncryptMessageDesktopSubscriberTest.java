package edu.calpoly.lambdalab.aidenrodriguez;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class EncryptMessageDesktopSubscriberTest {
    private static final String ID = "14caa8ee-ca63-41f4-b57f-074007b2affc";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void parsesAssignedFunctionEvent() throws Exception {
        EncryptMessageDesktopSubscriber.EncryptEvent event = EncryptMessageDesktopSubscriber.parseEvent(
                "{\"id\":\"" + ID + "\",\"operation\":\"EncryptMessageLambda\","
                        + "\"result\":{\"original\":\"Hello\",\"encrypted\":\"Khoor\"}}");

        assertEquals(ID, event.id());
        assertEquals("EncryptMessageLambda", event.operation());
        assertEquals("Khoor", event.result().path("encrypted").asText());
    }

    @Test
    void rejectsEventForAnotherOperation() {
        assertThrows(IllegalArgumentException.class, () -> EncryptMessageDesktopSubscriber.parseEvent(
                "{\"id\":\"" + ID + "\",\"operation\":\"OtherLambda\",\"result\":1}"));
    }

    @Test
    void acknowledgementPreservesIdAndUsesReceivedStatus() throws Exception {
        EncryptMessageDesktopSubscriber.EncryptEvent event = EncryptMessageDesktopSubscriber.parseEvent(
                "{\"id\":\"" + ID + "\",\"operation\":\"EncryptMessageLambda\",\"result\":1}");
        JsonNode acknowledgement = MAPPER.readTree(EncryptMessageDesktopSubscriber.acknowledgementFor(event));

        assertEquals("csc5100/status/" + ID,
            EncryptMessageDesktopSubscriber.statusTopicFor(event));
        assertEquals(ID, acknowledgement.path("id").asText());
        assertEquals("EncryptMessageLambda", acknowledgement.path("operation").asText());
        assertEquals("received", acknowledgement.path("status").asText());
    }

    @Test
    void rejectsEventWithoutUuid() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> EncryptMessageDesktopSubscriber.parseEvent(
                        "{\"id\":\"bad\",\"operation\":\"EncryptMessageLambda\",\"result\":1}"));
        assertTrue(error.getMessage().contains("UUID"));
    }
}
