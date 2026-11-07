package edu.calpoly.lambdalab.aidenrodriguez;

/** Persistence contract used by EncryptMessageLambda. */
interface Storage {
    void save(String id, String json);
    String get(String id);
}
