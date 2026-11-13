package edu.calpoly.lambdalab.oxlojalencas;

/**
 * builds the acknowledgment topic and json for an event id.
 *
 * @author Jess A
 * @version October 25, 2026
 */
final class NearestSampleAck {

    private NearestSampleAck() {
    }

    static String topicFor(String id) {
        return "csc5100/status/" + id;
    }

    // the id is a validated uuid, so it needs no json escaping
    static String jsonFor(String id) {
        return "{\"id\":\"" + id + "\",\"status\":\"acknowledged\"}";
    }
}
