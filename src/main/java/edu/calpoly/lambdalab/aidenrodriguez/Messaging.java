package edu.calpoly.lambdalab.aidenrodriguez;

/** Contract for publishing an encryption event to an external service. */
interface Messaging {
    void publish(String json);
}
