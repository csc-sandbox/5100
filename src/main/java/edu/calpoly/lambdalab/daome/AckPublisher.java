package edu.calpoly.lambdalab.daome;

import edu.calpoly.lambdalab.daome.data.EventStatus;

public interface AckPublisher extends AutoCloseable{

    void publish(String id, EventStatus status, String message);

    void close();
}
