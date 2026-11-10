package edu.calpoly.lambdalab.hwrd22;

/**
 * Defines the contract for publishing serialized JSON events for DataFreshnessLambda.
 *
 * @author Howard Jiang (hwrd22)
 * @version 1.0 (November 9, 2026)
 */
public interface Messaging {
    void publish(String json);
}
