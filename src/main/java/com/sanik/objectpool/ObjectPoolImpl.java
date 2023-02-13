package com.sanik.objectpool;

import java.io.IOException;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ObjectPoolImpl<T> implements ObjectPool<T>{

  private Supplier<T> supplier;
  private int minNumberOfObjects;
  private int maxNumberOfObjects;
  private volatile boolean stop;
  private final SyncHolder<T> syncHolder = new SyncHolder<T>();
  private volatile CountDownLatch objectCreatorLatch;
  private volatile int createdObjects = 0;
  private ExecutorService service;
  private EvictionPolicy<T> verificationPolicy;
  private OnClosePolicy<T> onClosePolicy;


  static class SyncHolder<T> {
    private ArrayBlockingQueue<T> readyObjects;
    private ArrayBlockingQueue<T> objectsForVerifycation;
    private LinkedList<T> borrowedObjects = new LinkedList<>();
  }

  public ObjectPoolImpl(Supplier<T> supplier, int minNumberOfObjects, int maxNumberOfObjects) {
    this.supplier = supplier;
    this.minNumberOfObjects = minNumberOfObjects;
    this.maxNumberOfObjects = maxNumberOfObjects;
    syncHolder.readyObjects = new ArrayBlockingQueue<>(maxNumberOfObjects);
    syncHolder.objectsForVerifycation = new ArrayBlockingQueue<>(maxNumberOfObjects);
    prepareLatch();
    service = Executors.newFixedThreadPool(4);
    service.submit(initObjectCreatorThread());
    service.submit(createObjectVerificationThread());
    service.submit(createObjectVerificationThread());
    service.submit(createObjectVerificationThread());
  }

  private Runnable createObjectVerificationThread() {
    return  () -> {
      while (!stop) {
        try {
          T objectForVerification = syncHolder.objectsForVerifycation.take();
          synchronized (syncHolder) {
            try {
              if (verifyObject(objectForVerification)) {
                syncHolder.readyObjects.put(objectForVerification);
              } else {
                createdObjects--;
              }
            } catch (ObjectPoolException e) {
              log.warn("Borrowed object cannot be verified ", e);
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    };
  }

  private void prepareLatch() {
    objectCreatorLatch = new CountDownLatch(1);
  }

  private Runnable initObjectCreatorThread() {
    return () -> {
      while (!stop) {
        boolean needToCreate;
        synchronized (syncHolder) {
          needToCreate = createdObjects < minNumberOfObjects || (syncHolder.readyObjects.size() == 0 && createdObjects < maxNumberOfObjects);
        }
        if(needToCreate) {
          T newObject = supplier.get();
          createdObjects++;
          if(newObject != null) {
            synchronized (syncHolder) {
              try {
                syncHolder.readyObjects.put(newObject);
              } catch (InterruptedException e) {
                e.printStackTrace();
              }
            }
          } else {
            throw new NullPointerException("Can't add new object to pool. Supplier returned null " + newObject);
          }
        } else {
          try {
            objectCreatorLatch.await(1, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            break;
          }
        }
      }
    };
  }

  @Override
  public T borrow() throws ObjectPoolException {
    T returnedObject = null;
    boolean needtoCreate;
    synchronized (syncHolder) {
      needtoCreate = syncHolder.borrowedObjects.size() < maxNumberOfObjects && syncHolder.objectsForVerifycation.isEmpty() && syncHolder.readyObjects.isEmpty();
    }
    try {
      if(needtoCreate) {
        objectCreatorLatch.countDown();
        objectCreatorLatch = new CountDownLatch(1);
      }
      returnedObject = syncHolder.readyObjects.take();

      synchronized (syncHolder) {
        syncHolder.borrowedObjects.add(returnedObject);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("Interruption detected");
    }

    return returnedObject;
  }

  @Override
  public void release(T object) throws ObjectPoolException {
    boolean remove;
    synchronized (syncHolder) {
      remove = syncHolder.borrowedObjects.remove(object);
    }
    if(remove) {
      syncHolder.objectsForVerifycation.add(object);
    } else if(onClosePolicy != null) {
      onClosePolicy.onClose(object);
    } else throw new ObjectPoolException("Not from pool");
  }

  @Override
  public boolean verifyObject(T object) throws ObjectPoolException {
    return verificationPolicy == null || verificationPolicy.isValid(object);
  }

  @Override
  public void close() throws IOException {
    if(stop) return;
    stop = true;
    service.shutdownNow();
    synchronized (syncHolder) {
      syncHolder.objectsForVerifycation.addAll(syncHolder.borrowedObjects);
      syncHolder.borrowedObjects.clear();
    }
    onCloseInQueue(syncHolder.readyObjects);
    onCloseInQueue(syncHolder.objectsForVerifycation);
  }

  private void onCloseInQueue(Queue<T> queue) {
    if (onClosePolicy == null) {
      queue.clear();
      return;
    }
    while (!queue.isEmpty()) {
      T element = queue.poll();
      if(element != null) {
        onClosePolicy.onClose(element);
      }
    }
  }

  @Override
  public int size() {
    return syncHolder.readyObjects.size();
  }

  public void setVerificationPolicy(EvictionPolicy<T> verificationPolicy) {
    this.verificationPolicy = verificationPolicy;
  }

  public void setOnClosePolicy(OnClosePolicy<T> onClosePolicy) {
    this.onClosePolicy = onClosePolicy;
  }

  @Override
  public String toString() {
    return "ObjectPoolImpl{" +
        "" + syncHolder.borrowedObjects + "/" + maxNumberOfObjects +
        '}';
  }
}
