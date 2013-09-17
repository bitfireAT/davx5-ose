package org.simpleframework.xml.core;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementUnion;

public class UnionNotRequiredTest extends ValidationTestCase {
   
   private static final String SOURCE =
   "<animalFarm>" +
   "  <dog><name>Snoopy</name></dog>" +
   "</animalFarm>";   
   
   private static final String MISSING_SOURCE =
   "<animalFarm/>";
   
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
   public static class AnimalFarm {   
      @ElementUnion({
         @Element(name="cat", required=false, type=Cat.class),
         @Element(name="dog", required=false, type=Dog.class),
         @Element(name="chicken", required=false, type=Chicken.class)
      })
      private Animal animal;
   }

   public void testOptionalEntry() throws Exception {
      Persister persister = new Persister();
      AnimalFarm example = persister.read(AnimalFarm.class, SOURCE);
      Animal animal = example.animal;
      assertNotNull(animal);
      assertEquals(animal.getClass(), Dog.class);
      assertEquals(animal.isMammal(), true);
      assertEquals(animal.getName(), "Snoopy");
      validate(persister, example);
   }
   
   public void testOptionalMissingEntry() throws Exception {
      Persister persister = new Persister();
      AnimalFarm example = persister.read(AnimalFarm.class, MISSING_SOURCE);
      Animal animal = example.animal;
      assertNull(animal);
      validate(persister, example);
   }
}
