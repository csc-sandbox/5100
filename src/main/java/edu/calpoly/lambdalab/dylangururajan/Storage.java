package edu.calpoly.lambdalab.dylangururajan;

/** Contract for persisting and retrieving coordinate-normalization results. */
interface Storage {
  void save(String id, String json);

  String get(String id);
}
