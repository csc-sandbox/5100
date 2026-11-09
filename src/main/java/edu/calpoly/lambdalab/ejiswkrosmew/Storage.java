package edu.calpoly.lambdalab.ejiswkrosmew;

public interface Storage {
    void save(String id, String json);

    String get(String id);
}
