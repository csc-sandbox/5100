package edu.calpoly.lambdalab.briggsmurphy;

/**
 * Defines queue publishing operations for LidarClosestLambda.
 *
 * @author Briggs Murphy
 * @version 1.0
 */
public interface QueuePublisher {

  void send(String json);
}
