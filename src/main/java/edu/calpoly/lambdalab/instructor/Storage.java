package edu.calpoly.lambdalab.instructor;

/**
 * Defines operations for persisting and retrieving JSON records by identifier.
 *
 * @author Javier Gonzalez-Sanchez
 * @version 2.0 (2026-10-29)
 */
public interface Storage {
  void save(String id, String json);

  String get(String id);
}
