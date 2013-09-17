package org.simpleframework.xml.util;

import java.util.HashMap;

import org.simpleframework.xml.util.WeakCache;

import junit.framework.TestCase;

public class WeakCacheTest extends TestCase {
   
   private static final int LOAD_COUNT = 100000;
   
   public void testCache() {
      WeakCache cache = new WeakCache();
      HashMap map = new HashMap();
      
      for(int i = 0; i < LOAD_COUNT; i++) {
         String key = String.valueOf(i);
         
         cache.cache(key, key);
         map.put(key, key);
      }
      for(int i = 0; i < LOAD_COUNT; i++) {
         String key = String.valueOf(i);
         
         assertEquals(cache.fetch(key), key);
         assertEquals(map.get(key), cache.fetch(key));
      }      
   }

}
