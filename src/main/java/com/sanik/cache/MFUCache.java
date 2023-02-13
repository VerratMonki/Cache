package com.sanik.cache;

import com.sanik.cache.veto.AddingVeto;
import com.sanik.cache.veto.RemovingVeto;
import com.sanik.cache.veto.UpdatingVeto;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MFUCache <K, V> implements Closeable, AutoCloseable {
  private final SyncHolder<K, V> syncHolder = new SyncHolder<>();
  private final EvictionPolicy<K, V> policy;
  private AddingVeto<K, V> addingVeto = (key, value) -> true;
  private RemovingVeto<K, V> removingVeto = (key, value) -> true;
  private UpdatingVeto<K, V> updatingVeto = (key, value) -> true;


  private final int capacity;
  private final int step;
  private long maxLifeTime;
  private volatile boolean stop;

  static class SyncHolder<K, V> {
    Map<K, Holder<V>>  vals = new HashMap<>();
    LinkedList<V> vList = new LinkedList<>(Holder::new);
  }

  public MFUCache(int capacity, int step, long maxLifeTime) {
    this.capacity = capacity;
    this.step = step;
    this.maxLifeTime = maxLifeTime;
    policy = entry -> (System.currentTimeMillis() - entry.getValue().getAddedTime()) > maxLifeTime;
    initThread();
  }

  public MFUCache(int capacity, int step, long maxLifeTime, EvictionPolicy<K, V> policy) {
    this.capacity = capacity;
    this.step = step;
    this.maxLifeTime = maxLifeTime;
    this.policy = policy;
    initThread();
  }

  private void initThread() {
    Runnable runnable = () -> {
      while (!stop) {
        synchronized (syncHolder) {
          syncHolder.vals.entrySet().stream()
              .filter(policy::needToBeDeleted)
              .map(Entry::getKey)
              .forEach(this::remove);
        }
        try {
          TimeUnit.SECONDS.sleep(30);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }

    };
    Thread thread = new Thread(runnable);
    thread.setName("Cache cleaner");
    thread.start();
  }

  public V get(K key) {
    Holder<V> holder;
    synchronized (syncHolder) {
      holder = syncHolder.vals.get(key);
    }
    if (holder == null) {
      return null;
    }
    synchronized (syncHolder) {
      moveToDesiredPosition(holder);
    }
    return holder.getValue();
  }

  public void put(K key, V value) {
    Holder<V> holder;
    synchronized (syncHolder) {
      holder = syncHolder.vals.get(key);
    }
    if(holder == null && addingVeto.operationAllowed(key, value)) {
      synchronized (syncHolder) {
        syncHolder.vals.put(key, holder = new Holder<>(null));
      }
    } else if(!updatingVeto.operationAllowed(key, value) || holder == null) {
      return;
    }
    holder.setValue(value);
    synchronized (syncHolder) {
      moveToDesiredPosition(holder);
    }
  }

  private void moveToDesiredPosition(Holder<V> holder) {
    syncHolder.vList.moveNodeWithStep(step, holder);
    reduceSizeIfNeeded();
  }

  private void reduceSizeIfNeeded() {
    if(syncHolder.vList.size() > capacity) {
      syncHolder.vList.removeLast();
    }
  }

  public List<V> values() {
    synchronized (syncHolder) {
      List<V> result = new ArrayList<>();
      for (int i = 0; i < syncHolder.vList.size(); i++) {
        result.add(syncHolder.vList.get(i).getValue());
      }
      return result;
    }
  }

  LinkedList<V> getList() {
    synchronized (syncHolder) {
      return syncHolder.vList;
    }
  }

  public V remove(K key){
      synchronized (syncHolder) {
        if(removingVeto.operationAllowed(key, syncHolder.vals.get(key).getValue())) {
        Holder<V> holder = syncHolder.vals.get(key);
        if (holder != null) {
          V result = holder.getValue();
          holder.setValue(null);
          return result;
        }
      }
    }
    return null;
  }

  /**
   * Returns amoubt of all keys in cache, whatever values are in there (maybe null. not a real value)
   * @return
   */
  public int size() {
    synchronized (syncHolder) {
      return syncHolder.vals.size();
    }
  }

  public void setAddingVeto(AddingVeto<K, V> addingVeto) {
    this.addingVeto = addingVeto;
  }

  public void setRemovingVeto(RemovingVeto<K, V> removingVeto) {
    this.removingVeto = removingVeto;
  }

  public void setUpdatingVeto(UpdatingVeto<K, V> updatingVeto) {
    this.updatingVeto = updatingVeto;
  }

  static class Holder<V> implements Node<V> {
    private Holder<V> previous;

    //Pointer to next element of list
    private Holder<V> next;

    //Value, which saved in current element of list
    private V value;

    private long addedTime;

    public Holder() {
    }

    public Holder(V value) {
      this.value = value;
      setAddedTime(System.currentTimeMillis());
    }

    @Override
    public Node<V> getPrevious() {
      return previous;
    }

    @Override
    public void setPrevious(Node<V> previous) {
      this.previous = (Holder<V>) previous;
    }

    @Override
    public Node<V> getNext() {
      return next;
    }

    @Override
    public void setNext(Node<V> next) {
      this.next = (Holder<V>) next;
    }

    @Override
    public V getValue() {
      return value;
    }

    @Override
    public void setValue(V value) {
      this.value = value;
      setAddedTime(System.currentTimeMillis());
    }

    @Override
    public String toString() {
      return "Holder{" +
          "value=" + value +
          '}';
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

  public void clear() {
    synchronized (syncHolder) {
      Set<K> toRemove = syncHolder.vals.entrySet().stream()
          .filter(entry -> removingVeto.operationAllowed(entry.getKey(), entry.getValue().getValue()))
          .map(Entry::getKey)
          .collect(Collectors.toSet());
      toRemove.forEach(key -> {
        syncHolder.vList.remove(syncHolder.vals.get(key).getValue());
        syncHolder.vals.remove(key);
      });
    }
  }

  public void close() {
    if (stop) return;
    //removes all
    synchronized (syncHolder) {
      syncHolder.vals.clear();
      syncHolder.vList.clear();
    }
    //stop the thread
    stop = true;
  }
}
