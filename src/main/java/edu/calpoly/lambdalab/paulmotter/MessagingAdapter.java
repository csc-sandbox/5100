package edu.calpoly.lambdalab.paulmotter;

/**
 * Defines the contract for publishing serialized JSON events.
 * Implementations keep the AWS messaging clients out of RobotDistanceLambda.
 *
 * @author Paul Motter
 * @version 1.0 (2026-11-10)
 */
interface MessagingAdapter{
    public void publish(String json);
}
