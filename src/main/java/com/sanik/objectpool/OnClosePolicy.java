package com.sanik.objectpool;

public interface OnClosePolicy<T> {
  void onClose(T t);
}
