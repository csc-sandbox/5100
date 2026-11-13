package edu.calpoly.lambdalab.adrian0427;

interface Storage {
    void save(String id, String json);
    String get(String id);
}
