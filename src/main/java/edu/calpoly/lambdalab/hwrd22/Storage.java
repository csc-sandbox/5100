package edu.calpoly.lambdalab.hwrd22;

/**
 * Defines operations for persisting and retrieving JSON records by identifier for DataFreshnessLambda.
 *
 * @author Howard Jiang (hwrd22)
 * @version 1.0 (November 9, 2026)
 */
public interface Storage {
    void save(String id, String json);

    String get(String id);
}
