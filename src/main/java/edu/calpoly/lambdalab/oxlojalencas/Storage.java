package edu.calpoly.lambdalab.oxlojalencas;

/**
 * saves and loads result json by id.
 *
 * @author Jess A
 * @version November 10, 2026
 */
interface Storage {

    void save(String id, String json);

    String get(String id);
}
