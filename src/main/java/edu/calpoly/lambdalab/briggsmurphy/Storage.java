package edu.calpoly.lambdalab.briggsmurphy;

/**
 * Defines storage operations for LidarClosestLambda.
 *
 * @author Briggs Murphy
 * @version 1.0
 */
public interface Storage {

  void save(String id, String json);

  String get(String id);

  void saveToS3(String id, String json);
}
