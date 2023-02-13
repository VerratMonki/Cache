package com.sanik.objectpool;

public interface EvictionPolicy<T>{
  boolean isValid(T t);
}
