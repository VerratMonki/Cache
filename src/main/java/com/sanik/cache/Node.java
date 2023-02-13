package com.sanik.cache;

public interface Node <T>{

  public Node<T> getPrevious();

  public void setPrevious(Node<T> previous);

  public Node<T> getNext();

  public void setNext(Node<T> next);

  public T getValue();

  public void setValue(T value);

  public void setAddedTime(long addedTime);

  public long getAddedTime();

}
