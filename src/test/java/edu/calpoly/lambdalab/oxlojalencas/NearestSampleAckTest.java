package edu.calpoly.lambdalab.oxlojalencas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * tests for NearestSampleAck.
 *
 * @author Jess A
 * @version October 25, 2026
 */
class NearestSampleAckTest {

    private static final String ID = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";

    @Test
    void buildsStatusTopicFromId() {
        assertEquals("csc5100/status/" + ID, NearestSampleAck.topicFor(ID));
    }

    @Test
    void buildsAcknowledgmentJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String expected = "{\"id\":\"" + ID + "\",\"status\":\"acknowledged\"}";

        assertEquals(mapper.readTree(expected),
                mapper.readTree(NearestSampleAck.jsonFor(ID)));
    }
}
