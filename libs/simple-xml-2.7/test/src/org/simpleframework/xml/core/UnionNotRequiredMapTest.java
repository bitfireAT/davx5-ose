package org.simpleframework.xml.core;

import java.util.Map;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementMapUnion;

public class UnionNotRequiredMapTest extends ValidationTestCase {
   
   private static final String SOURCE =
   "<farmExample>" +
   "  <cats>\n"+
   "     <animal key='1'><cat><name>Tom</name></cat></animal>\n" +
   "     <animal key='2'><cat><name>Dick</name></cat></animal>\n" +
   "     <animal key='3'><cat><name>Harry</name></cat></animal>\n" +   
   "  </cats>\n"+
   "</farmExample>";   
   
   private static final String MISSING_SOURCE =
   "<farmExample>" +
   "</farmExample>";   
   
   public static class Cat extends Animal {
      public boolean isMammal(){
         return true;
      }
   }
   
   public static class Dog extends Animal {
      public boolean isMammal(){
         return true;
      }
   }
   
   public static class Chicken extends Animal {
      public boolean isBird() {
         return true;
      }
   }
   @Default
   public static class Animal {
      private String name;
      public String getName(){
         return name;
      }
      public boolean isMammal(){
         return false;
      }
      public boolean isBird(){
         return false;
      }
      public boolean isFish(){
         return false;
      }
   }
   
   @Root
   public static class FarmExample {   
      @ElementMapUnion({
         @ElementMap(name="cats", entry="animal", key="key", required=false, attribute=true, valueType=Cat.class),
         @ElementMap(name="dogs", entry="animal", key="key", required=false, attribute=true, valueType=Dog.class),
         @ElementMap(name="chickens", entry="animal", key="key", required=false, attribute=true, valueType=Chicken.class)
      })
      private Map<String, Animal> farm;
   }

   public void testOptionalMap() throws Exception {
      Persister persister = new Persister();
      FarmExample example = persister.read(FarmExample.class, SOURCE);
      Map<String, Animal> farm = example.farm;
      assertEquals(farm.size(), 3);
      assertEquals(farm.get("1").getClass(), Cat.class);
      assertEquals(farm.get("1").getName(), "Tom");
      assertTrue(farm.get("1").isMammal());
      assertEquals(farm.get("2").getClass(), Cat.class);
      assertEquals(farm.get("2").getName(), "Dick");
      assertTrue(farm.get("2").isMammal());
      assertEquals(farm.get("3").getClass(), Cat.class);
      assertEquals(farm.get("3").getName(), "Harry");
      assertTrue(farm.get("3").isMammal());      
   }
   
   public void testMissingOptionalMap() throws Exception {
      Persister persister = new Persister();
      FarmExample example = persister.read(FarmExample.class, MISSING_SOURCE);
      Map<String, Animal> farm = example.farm;
      assertNull(farm);    
   }
}
