package edu.calpoly.lambdalab.hwrd22;

public interface Storage {
    void save(String id, String json);

    String get(String id);
}
