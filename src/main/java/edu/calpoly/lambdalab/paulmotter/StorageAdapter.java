package edu.calpoly.lambdalab.paulmotter;

/**
 * Defines operations for persisting and retrieving JSON records by identifier.
 * Implementations keep the AWS storage clients out of RobotDistanceLambda.
 *
 * @author Paul Motter
 * @version 1.0 (2026-11-10)
 */
interface StorageAdapter<T>{
    public void save(T data, String json);
    public String get(String id);
}