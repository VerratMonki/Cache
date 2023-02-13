package com.sanik.cache;

import java.util.function.Supplier;

public class LinkedList<T> {
  private Supplier<Node<T>> supplier = LinkedNode::new;
  //pointer to first object in list
  private Node<T> first = null;

  //pointer to last object in array
  private Node<T> last = null;

  //Number of elements in array
  private int size = 0;

  public LinkedList() {
  }

  public LinkedList(Supplier<Node<T>> supplier) {
    this();
    this.supplier = supplier;
  }

  /**
   * Default method to add element to list. In add's to end of list
   *
   * @param t - object, which need to save
   */
  public void add(T t) {
    if (size == 0) {
      addFirstNode(t);
    } else {
      addLast(t);
    }
  }

  /**
   * Method to get first element in list
   *
   * @return first elements
   */
  public T getFirst() {
    return (first != null) ? first.getValue() : null;
  }

  /**
   * Method to get last element in list
   *
   * @return last elements
   */
  public T getLast() {
    return (last != null) ? last.getValue() : null;
  }

  /**
   * Method to return actual size
   *
   * @return actual size of array
   */
  public int size() {
    return size;
  }

  /**
   * Method to save element first in list
   *
   * @param t - object, which need to save on first position in list
   */
  public void addFirst(T t) {
    Node<T> node = supplier.get();
    node.setValue(t);
    addToStart(node);
  }

  /**
   * Method to save element last in list
   *
   * @param t - object, which need to save on last position in list
   */
  public void addLast(T t) {
    Node<T> node = supplier.get();
    node.setValue(t);
    addToEnd(node);
  }

  /**
   * Method to delete element on first position
   */
  public void removeFirst() {
    removeFromStart();
  }

  /**
   * Method to delete element on last position
   */
  public void removeLast() {
    removeFromEnd();
  }

  /**
   * Method to delete object from list with position, which equals input index
   *
   * @param index - position of element, which need o delete
   * @return true, if secceddfully deleted and false, if not
   */
  public boolean remove(int index) {
    try {
      if (index >= 0 && index < size) {
        Node<T> currentNode = first;
        for (int i = 0; i < index; i++) {
          if (currentNode != null) {
            currentNode = currentNode.getNext();
          }
        }
        assert currentNode != null;
        Node<T> previousNode = currentNode.getPrevious();
        if (previousNode == null) {
          Node<T> nextNode = currentNode.getNext();
          if (nextNode != null) {
            nextNode.setPrevious(null);
            first = nextNode;
          }
          size--;
          return true;
        }
        Node<T> nextNode = currentNode.getNext();
        if (nextNode == null) {
          previousNode.setNext(null);
          last = previousNode;
          size--;
          return true;
        }
        previousNode.setNext(nextNode);
        nextNode.setPrevious(previousNode);
        size--;
        return true;
      }
      throw new IndexOutOfBoundsException("There is no element with such index: " + index);
    } catch (Exception ex) {
      return false;
    }
  }

  /**
   * Method to delete input object from list
   *
   * @param t - object, which need to delete from list
   * @return true, if secceddfully deleted and false, if not
   */
  public boolean remove(T t) {
    return remove(indexOf(t));
  }

  public void moveNodeWithStep(int step, Node<T> node) {
    if(node.getPrevious() == null && node.getNext() == null) {
      addToEnd(node);
    }
    moveNode(step, node);
  }

  private void moveNode(int step, Node<T> node) {
    step ++;
    Node<T> currentNode = node;
    if(currentNode == null || node == first) {
      return;
    }
    if(node.getPrevious() != null) {
      node.getPrevious().setNext(node.getNext());
    }
    if(node.getNext() != null) {
      node.getNext().setPrevious(node.getPrevious());
    } else {
      last = node.getPrevious();
    }
    while (step > 0) {
      currentNode = currentNode.getPrevious();
      if(currentNode == null) {
        switchPointers(null, node, first);
        first = node;
        return;
      }
      step --;
    }
    switchPointers(currentNode, node, currentNode.getNext());
  }

  private void switchPointers(Node<T> previousNode, Node<T> node, Node<T> nextNode) {
    if(node == null)  {
      return;
    }
    if(previousNode != null) {
      previousNode.setNext(node);
    }
    node.setPrevious(previousNode);
    node.setNext(nextNode);
    if(nextNode != null) {
      nextNode.setPrevious(node);
    }
  }

  public synchronized Node<T> getNode(T t) {
    if (t == null) {
      return null;
    }
    Node<T> currentNode = first;
    for (int i = 1; i <= size; i++) {
      if(currentNode != null && currentNode.getValue().equals(t)) {
        return currentNode;
      }
      if (currentNode != null) {
        currentNode = currentNode.getNext();
      }
    }
    return null;
  }

  /**
   * Method to return, if input object saved in list
   *
   * @param t - object, which need to check, if it saved in list
   * @return true, if input object saved in list and false, if not
   */
  public boolean contains(T t) {
    return indexOf(t) >= 0;
  }

  /**
   * Method to get position in list of input object
   *
   * @param obj - object, which need to check
   * @return index of position in list, where this element saved
   */
  public int indexOf(Object obj) {
    Node<T> currentNode = first;
    for (int i = 0; i < size; i++) {
      if (obj.equals(currentNode.getValue())) {
        return i;
      }
      currentNode = currentNode.getNext();
    }
    return Integer.MIN_VALUE;
  }

  /**
   * Method to return is empty the array
   *
   * @return true, if array is empty, and false, if not
   */
  public boolean isEmpty() {
    return size == 0;
  }

  /**
   * Method to delete element from end of list
   */
  private void removeFromEnd() {
    if (size > 0) {
      Node<T> newLast = last.getPrevious();
      last.setPrevious(null);
      if (newLast != null) {
        newLast.setNext(null);
      }
      last = newLast;
      if (size == 1) {
        first = null;
      }
      size--;
    } else {
      throw new IndexOutOfBoundsException("There is no elements in array. Please, add some");
    }
  }

  /**
   * Method to delete element from start of list
   */
  private void removeFromStart() {
    if (size > 0) {
      Node<T> newFirst = first.getNext();
      first.setNext(null);
      if (newFirst != null) {
        newFirst.setPrevious(null);
      }
      first = newFirst;
      if (size == 1) {
        last = null;
      }
      size--;
    } else {
      throw new IndexOutOfBoundsException("There is no elements in array. Please, add some");
    }
  }

  /**
   * Method to save input object to end of list
   *
   * @param node - object, which need to save on end position of list
   */
  private void addToEnd(Node<T> node) {
    if (size == 0) {
      first = node;
    } else {
      Node<T> previousLastNode = last;
      previousLastNode.setNext(node);
      node.setPrevious(previousLastNode);
    }
    last = node;
    size++;
  }

  /**
   * Method to return String with elements of array
   *
   * @return String with elements of array
   */
  public String toString() {
    String arrayElements = "{";
    Node currentNode = first;
    for (int i = 0; i < size; i++) {
      if (currentNode != null) {
        arrayElements += currentNode.getValue();
        if (i < size - 1) {
          arrayElements += ", ";
        }
        currentNode = currentNode.getNext();
      } else {
        break;
      }
    }
    arrayElements += "}";
    return arrayElements;
  }

  /**
   * Method to save input object to start of list
   *
   * @param node - object, which need to save on start position of list
   */
  private void addToStart(Node<T> node) {
    if (size == 0) {
      last = node;
    } else {
      Node<T> previousStartNode = first;
      previousStartNode.setPrevious(node);
      node.setNext(previousStartNode);
    }
    first = node;
    size++;
  }

  /**
   * Method to add first element in list (on both positions)
   *
   * @param t - object, which saved on both positions
   */
  private void addFirstNode(T t) {
    Node<T> node = supplier.get();
    node.setValue(t);
    first = node;
    last = node;
    size++;
  }

  public synchronized Node<T> get(int index) {
    Node<T> currentNode = first;
    for (int i = 0; i < index; i++) {
      if(currentNode != null) {
        currentNode = currentNode.getNext();
      }
    }
    return currentNode;
  }

  public void clear() {
    Node<T> currentNode = first;
    Node<T> nextNode;
    while (currentNode != null){
      currentNode.setPrevious(null);
      nextNode = currentNode.getNext();
      currentNode.setNext(null);
      currentNode.setValue(null);
      currentNode = nextNode;
    }
    first = null;
    last = null;
  }
}

class LinkedNode<T> implements Node<T>{

  //Pointer to previous element of list
  private Node<T> previous;

  //Pointer to next element of list
  private Node<T> next;

  //Value, which saved in current element of list
  private T value;

  private long addedTime;

  @Override
  public Node<T> getPrevious() {
    return previous;
  }

  @Override
  public void setPrevious(Node<T> previous) {
    this.previous = previous;
  }

  @Override
  public Node<T> getNext() {
    return next;
  }

  @Override
  public void setNext(Node<T> next) {
    this.next = next;
  }

  @Override
  public T getValue() {
    return value;
  }

  @Override
  public void setValue(T value) {
    this.value = value;
    setAddedTime(System.currentTimeMillis());
  }

  @Override
  public void setAddedTime(long addedTime) {
    this.addedTime = addedTime;
  }

  @Override
  public long getAddedTime() {
    return addedTime;
  }
}