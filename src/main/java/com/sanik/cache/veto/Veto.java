package com.sanik.cache.veto;

public interface Veto<K, V> {
  boolean operationAllowed(K key, V value);
}
