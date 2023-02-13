package com.sanik.cache;

import java.util.Map.Entry;

public interface EvictionPolicy <K, V> {

  boolean needToBeDeleted(Entry<K, ? extends Node<V>> kHolderEntry);
}
