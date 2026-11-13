package edu.calpoly.lambdalab.instructor;

/**
 * Defines the contract for publishing serialized JSON events.
 *
 * @author Javier Gonzalez-Sanchez
 * @version 2.0 (2026-10-29)
 */
public interface Messaging {
  void publish(String json);
}
