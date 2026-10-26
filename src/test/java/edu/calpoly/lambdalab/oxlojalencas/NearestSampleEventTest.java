package edu.calpoly.lambdalab.oxlojalencas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * tests for NearestSampleEvent.
 *
 * @author Jess A
 * @version October 25, 2026
 */
class NearestSampleEventTest {

    private static final String ID = "c5f1b1b7-ccf5-4b98-80f9-f1123589a123";

    private static final String EVENT = "{\"id\":\"" + ID + "\","
            + "\"operation\":\"NearestSampleLambda\","
            + "\"result\":{\"nearestSample\":{\"timestamp\":10.4,\"value\":5.1},"
            + "\"timeDifference\":0.1}}";

    @Test
    void parsesEventFields() {
        NearestSampleEvent event = NearestSampleEvent.parse(EVENT);

        assertEquals(ID, event.id());
        assertEquals(10.4, event.timestamp(), 0.0001);
        assertEquals(5.1, event.value(), 0.0001);
        assertEquals(0.1, event.timeDifference(), 0.0001);
    }

    @Test
    void rejectsInvalidEvents() {
        List<String> badEvents = List.of(
                "not json",
                EVENT.replace(ID, "bad-id"),
                EVENT.replace("NearestSampleLambda", "OtherLambda"),
                "{\"id\":\"" + ID + "\",\"operation\":\"NearestSampleLambda\"}",
                EVENT.replace(",\"value\":5.1", ""));

        for (String badEvent : badEvents) {
            assertThrows(IllegalArgumentException.class,
                    () -> NearestSampleEvent.parse(badEvent));
        }
    }

    @Test
    void summaryShowsEventDetails() {
        String summary = NearestSampleEvent.parse(EVENT).summary();

        assertTrue(summary.contains(ID));
        assertTrue(summary.contains("10.4"));
        assertTrue(summary.contains("5.1"));
        assertTrue(summary.contains("0.1"));
    }
}
