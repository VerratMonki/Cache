package com.sanik.objectpool;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.lang.Thread.State;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import jdk.jfr.Unsigned;
import org.junit.jupiter.api.Test;

class ObjectPoolImplTest {

  @Test
  public void usualUse() {
    StringBuilder stringBuilder = null;
    try (ObjectPool<StringBuilder> objectPool = new ObjectPoolImpl<>(StringBuilder::new, 1, 1)) {
      stringBuilder = objectPool.borrow();
      stringBuilder.append("Test succesfull");
    } catch (Exception objectPoolException) {
      fail();
    } finally {
      assertNotNull(stringBuilder);
      assertEquals("Test succesfull", stringBuilder.toString());
    }
  }

  @Test
  public void objectInstanceTest() {
    try (ObjectPool<A> objectPool = new ObjectPoolImpl<>(A::new, 1, 1);) {
      A a = objectPool.borrow();
      assertNotNull(a);
    } catch (Exception e) {
      fail();
    }
  }

  @Test
  public void returnSameObject() {
    StringBuilder stringBuilder = null;
    try (ObjectPool<StringBuilder> objectPool = new ObjectPoolImpl<>(StringBuilder::new, 1, 1)) {
      stringBuilder = objectPool.borrow();
      stringBuilder.append("Test ");
      objectPool.release(stringBuilder);
      StringBuilder sameObject = objectPool.borrow();
      assertSame(stringBuilder, sameObject);
      stringBuilder.append("succesfull");
    } catch (Exception e) {
      fail();
    } finally {
      assertNotNull(stringBuilder);
      assertEquals("Test succesfull", stringBuilder.toString());
    }
  }

  @Test
  public void waitIfObjectPoolEmpty() throws InterruptedException, ObjectPoolException {
    StringBuilder firstObject;
    StringBuilder secondObject = null;
    AtomicReference<StringBuilder> objectFromThread = new AtomicReference<>();
    try (ObjectPool<StringBuilder> objectPool = new ObjectPoolImpl<>(StringBuilder::new, 1, 2)) {
      firstObject = objectPool.borrow();
      secondObject = objectPool.borrow();
      assertNotNull(firstObject);
      assertNotNull(secondObject);

      Runnable getObjectFromEmptyObjectPool = () -> {
        try {
          objectFromThread.set(objectPool.borrow());
          assertNotNull(objectFromThread);
        } catch (ObjectPoolException objectPoolException) {
          objectPoolException.printStackTrace();
        }
      };
      Thread thread = new Thread(getObjectFromEmptyObjectPool);
      thread.start();
      TimeUnit.SECONDS.sleep(1);

      assertEquals(State.WAITING, thread.getState());
      objectPool.release(secondObject);
      TimeUnit.SECONDS.sleep(1);
      assertSame(secondObject, objectFromThread.get());
      assertEquals(State.TERMINATED, thread.getState());
    } catch (Exception e) {
      fail();
    }
  }

  public void multiThreadingUsage() {
    int numberOfObjects = 5;
    CountDownLatch latch = null;
    try (ObjectPool<A> objectPool = new ObjectPoolImpl<>(A::new, numberOfObjects, numberOfObjects)) {
      System.out.println(objectPool.size());
      List<A> list = assignNames(numberOfObjects, objectPool);
      list.clear();
      CountDownLatch finalLatch = latch;
      Runnable runnable = () -> {
        try {
          for(int j = 0; j < 10; j++) {
            A a = objectPool.borrow();
            System.out.println("Got " + a.getName());
            a.increment();
            System.out.println(a.getA());
            objectPool.release(a);
            System.out.println("Returned " + a.getName());
          }
          System.out.println("Thread incremented A successfully");
          finalLatch.countDown();
        }catch (ObjectPoolException e) {
          System.err.println("Problem");
          fail();
        }
      };
      ExecutorService service = Executors.newFixedThreadPool(numberOfObjects);
      for (int i = 0; i < 1_000_000; i++) {
        latch = new CountDownLatch(numberOfObjects);
        for(int k = 0; k < numberOfObjects; k++) {
          service.submit(runnable);
        }
        latch.await();
        System.out.println("Latch opened");
        int sum = 0;
        List<A> objects = new ArrayList<>();
        for(int k = 0; k < numberOfObjects; k++) {
          A object = objectPool.borrow();
          sum += object.getA();
          object.initA();
          objects.add(object);
        }
        for (A a : objects) {
          objectPool.release(a);
        }
        assertEquals(10 * numberOfObjects, sum);
        System.out.println("Test passed");
        service.shutdownNow();
      }
    }catch (Exception e) {
      fail();
    }
  }

  @Test
  public void testManyThreads() throws InterruptedException, IOException {
    ExecutorService service = Executors.newFixedThreadPool(100);
    ObjectPool<A> objectPool = new ObjectPoolImpl<>(A::new, 2, 5);
    assignNames(5, objectPool);
    try  {
      for (int i = 0; i < 1_000_000; i++) {
        service.submit(() -> {
          A toBeIncremented = null;
          try {
            toBeIncremented = objectPool.borrow();
            toBeIncremented.increment();
          } finally {
            objectPool.release(toBeIncremented);
          }
        });
      }
    } catch (Exception ex) {
      //
    }
    service.shutdown();
    System.err.println("waiting for finish...");
    assertTrue(service.awaitTermination(20, TimeUnit.SECONDS));
    System.err.println("finished.");
    long sum =0;
    for(int k = 0; k < 5; k++) {
      A object = objectPool.borrow();
      sum += object.getA();
      System.err.println("++"+object.getName());
    }
    assertEquals(1_000_000, sum);
    objectPool.close();
  }

  private List<A> assignNames(int numberOfObjects, ObjectPool<A> objectPool) throws ObjectPoolException {
    List<A> list = new ArrayList<>();
    for(int i = 0; i < numberOfObjects; i++) {
      A a = objectPool.borrow();
      a.setName("Object " + (i+1));
      list.add(a);
    }
    for (A a : list) {
      objectPool.release(a);
    }
    return list;
  }
}

class A {
  private AtomicInteger a = new AtomicInteger();
  private String name;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public void increment() {
    a.incrementAndGet();
  }

  public void initA() {
    a.set(0);
  }

  public int getA() {
    return a.get();
  }

  @Override
  public String toString() {
    return "A{" +
        "a=" + a +
        ", name='" + name + '\'' +
        '}';
  }
}