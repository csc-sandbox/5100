package edu.calpoly.lambdalab.dylangururajan;

/** Contract for publishing a coordinate-normalization result event. */
interface Messaging {
  void publish(String json);
}
