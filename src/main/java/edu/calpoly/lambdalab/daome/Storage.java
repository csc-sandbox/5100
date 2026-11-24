package edu.calpoly.lambdalab.daome;

import edu.calpoly.lambdalab.daome.Exceptions.StorageException;

public interface Storage {
    void save(String id, String json) throws StorageException;

    String get(String id) throws StorageException;
}
