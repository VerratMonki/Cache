package com.sanik.objectpool;

import java.io.Closeable;

public interface ObjectPool<T> extends Closeable, AutoCloseable {
  T borrow() throws ObjectPoolException;
  void release(T object) throws ObjectPoolException;
  boolean verifyObject(T object) throws ObjectPoolException;
  int size();
}
