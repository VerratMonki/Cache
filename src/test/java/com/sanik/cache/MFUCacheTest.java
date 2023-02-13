package com.sanik.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.sanik.cache.veto.AddingVeto;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MFUCacheTest {

  @Test
  public void usualUse() {
    MFUCache<Integer, String> cache = new MFUCache<>(3, 1, 60_000);
    cache.put(13, "Kyiv");
    cache.put(94, "Buda");
    cache.put(34, "Java");

    assertEquals("Kyiv", cache.get(13));
    assertEquals("Buda", cache.get(94));
    assertEquals("Java", cache.get(34));
    cache.close();
  }

  @Test
  public void usualUseWithStep() {
    MFUCache<Integer, String> cache = new MFUCache<>(3, 1, 60_000);
    cache.put(13, "Kyiv");
    cache.put(94, "Buda");
    cache.put(34, "Java");

    assertEquals(Arrays.asList("Buda", "Java", "Kyiv"), cache.values());
    cache.put(71, "Puma");
    assertEquals(Arrays.asList("Buda", "Java", "Puma"), cache.values());
    cache.get(71);
    assertEquals(Arrays.asList("Buda", "Puma", "Java"), cache.values());
    cache.close();
  }

  @Test
  public void checkMaxLifeTime() {
    MFUCache<Integer, String> cache = new MFUCache<>(3, 1, 30_000);
    cache.put(13, "Kyiv");
    cache.put(94, "Buda");
    cache.put(34, "Java");

    try {
      TimeUnit.SECONDS.sleep(35);
    }catch (InterruptedException ex) {
      fail();
    }
    assertNull(cache.get(13));
    assertNull(cache.get(94));
    assertNull(cache.get(34));
  }

  @Test
  public void checkAddingVeto() {
    MFUCache<Integer, String> cache = new MFUCache<>(3, 1, 30_000);
    cache.setAddingVeto((key, value) -> key % 2 == 0 && key < 50);
    cache.put(13, "Kyiv");
    cache.put(94, "Buda");
    cache.put(34, "Java");

    assertNull(cache.get(13));
    assertNull(cache.get(94));
    assertEquals("Java", cache.get(34));
  }

  @Test
  public void checkUpdatingVeto() {
    MFUCache<Integer, String> cache = new MFUCache<>(3, 1, 30_000);
    cache.put(13, "Kyiv");
    cache.setAddingVeto((key, value) -> key % 2 == 0 && key < 50);
    cache.setUpdatingVeto((key, value) -> value.length() < 5);
    cache.put(13, "Wrong value");

    assertEquals("Kyiv", cache.get(13));
  }

  @Test
  public void checkRemovingVeto() {
    MFUCache<Integer, String> cache = new MFUCache<>(3, 1, 30_000);
    cache.put(13, "Kyiv");
    cache.put(94, "Buda");
    cache.put(34, "Java");
    cache.put(71, "Banzai");
    cache.put(131, "Must be removed");
    cache.setRemovingVeto((key, value) -> key > 50);
    cache.clear();

    assertEquals("Kyiv", cache.get(13));
    assertEquals("Java", cache.get(34));
    assertNull(cache.get(94));
    assertNull(cache.get(71));
    assertNull(cache.get(131));
  }

  @Test
  public void getIfNotContains() {
    MFUCache<Integer, String> cache = new MFUCache<>(3, 1, 60_000);
    assertNull(cache.get(11));
    cache.close();
  }

  @Test
  public void addValueWhichAlreadyInCache() {
    MFUCache<Integer, String> cache = new MFUCache<>(3, 1, 60_000);
    cache.put(11, "Kyiv");
    cache.put(11, "Puma");

    assertEquals("Puma", cache.get(11));
    cache.close();
  }

  @Test
  public void multiThreadPut() throws InterruptedException {
    MFUCache<Integer, String> cache = new MFUCache<>(3, 1,60_000);
    int numberThreads = 10;
    ExecutorService service = Executors.newFixedThreadPool(numberThreads);
    for (int i = 0; i < 100_000; i++) {
      for (int j = 0; j < numberThreads; j++) {
        int finalJ = j;
        service.submit(() -> {
          cache.put(finalJ, String.valueOf(finalJ));
        });
      }
    }
    service.shutdown();
    service.awaitTermination(20, TimeUnit.SECONDS);

    for (int j = 0; j < numberThreads; j++) {
       assertEquals(String.valueOf(j), cache.get(j));
    }
    LinkedList<String> result = cache.getList();
    assertEquals(3, result.size());

    assertNull(result.get(0).getPrevious());
    for(int i = 1; i < result.size(); i++) {
      assertEquals(result.get(i), result.get(i-1).getNext());
      assertEquals(result.get(i-1), result.get(i).getPrevious());
    }
    assertNull(result.get(result.size()-1).getNext());
    cache.close();
  }

  @Test
  public void multiThreadGet() throws InterruptedException {
    int numberThreads = 10;
    MFUCache<Integer, String> cache = new MFUCache<>(numberThreads, 1, 60_000);
    for(int i = 0; i < 7; i++) {
      cache.put(i, String.valueOf(i));
    }
    ExecutorService service = Executors.newFixedThreadPool(numberThreads);
    for (int i = 0; i < 100_000; i++) {
      for (int j = 0; j < numberThreads; j++) {
        int finalJ = j;
        service.submit(() -> {
          if(finalJ < 5) {
            assertEquals(String.valueOf(finalJ), cache.get(finalJ));
          } else  {
            assertNull(cache.get((int) (Math.random() * 100 + 10)));
          }
        });
      }
    }
    service.shutdown();
    service.awaitTermination(20, TimeUnit.SECONDS);

    LinkedList<String> result = cache.getList();
    assertEquals(7, result.size());
    for (int j = 0; j < 7; j++) {
      assertEquals(String.valueOf(j), cache.get(j));
      assertEquals(cache.get(j), result.getNode(String.valueOf(j)).getValue());
    }

    assertNull(result.get(0).getPrevious());
    for(int i = 1; i < result.size(); i++) {
      assertEquals(result.get(i), result.get(i-1).getNext());
      assertEquals(result.get(i-1), result.get(i).getPrevious());
    }
    assertNull(result.get(result.size()-1).getNext());
    cache.close();
  }
}