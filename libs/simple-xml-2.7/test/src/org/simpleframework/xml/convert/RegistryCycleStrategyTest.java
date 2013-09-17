package org.simpleframework.xml.convert;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.convert.ExampleConverters.Cat;
import org.simpleframework.xml.convert.ExampleConverters.CatConverter;
import org.simpleframework.xml.convert.ExampleConverters.Dog;
import org.simpleframework.xml.convert.ExampleConverters.DogConverter;
import org.simpleframework.xml.convert.ExampleConverters.Pet;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.CycleStrategy;

public class RegistryCycleStrategyTest extends ValidationTestCase {

   @Root
   public static class PetBucket {
      @ElementList(inline=true)
      private List<Pet> list = new ArrayList<Pet>();
      public void addPet(Pet pet){
         list.add(pet);
      }
      public List<Pet> getPets(){
         return list;
      }
   }
   
   public void testCycle() throws Exception {
      Registry registry = new Registry();
      CycleStrategy inner = new CycleStrategy();
      RegistryStrategy strategy = new RegistryStrategy(registry, inner);
      Persister persister = new Persister(strategy);
      PetBucket bucket = new PetBucket();
      StringWriter writer = new StringWriter();
      
      registry.bind(Cat.class, CatConverter.class);
      registry.bind(Dog.class, DogConverter.class);
   
      Pet kitty = new Cat("Kitty", 10);
      Pet lassie = new Dog("Lassie", 7);
      Pet ben = new Dog("Ben", 8);
      
      bucket.addPet(kitty);
      bucket.addPet(lassie);
      bucket.addPet(ben);
      bucket.addPet(lassie);
      bucket.addPet(kitty);
      
      persister.write(bucket, writer);
      persister.write(bucket, System.out);
   
      String text = writer.toString();
      PetBucket copy = persister.read(PetBucket.class, text);
      
      assertEquals(copy.getPets().get(0), bucket.getPets().get(0));
      assertEquals(copy.getPets().get(1), bucket.getPets().get(1));
      assertEquals(copy.getPets().get(2), bucket.getPets().get(2));
      assertEquals(copy.getPets().get(3), bucket.getPets().get(3));
      assertEquals(copy.getPets().get(4), bucket.getPets().get(4));
      
      assertTrue(copy.getPets().get(0) == copy.getPets().get(4)); // cycle
      assertTrue(copy.getPets().get(1) == copy.getPets().get(3)); // cycle

      assertElementExists(text, "/petBucket");
      assertElementExists(text, "/petBucket/pet");
      assertElementHasAttribute(text, "/petBucket", "id", "0");
      assertElementHasAttribute(text, "/petBucket/pet[1]", "id", "1");
      assertElementHasAttribute(text, "/petBucket/pet[2]", "id", "2");
      assertElementHasAttribute(text, "/petBucket/pet[3]", "id", "3");
      assertElementHasAttribute(text, "/petBucket/pet[4]", "reference", "2");
      assertElementHasAttribute(text, "/petBucket/pet[5]", "reference", "1");
      assertElementHasValue(text, "/petBucket/pet[1]/name", "Kitty");
      assertElementHasValue(text, "/petBucket/pet[1]/age", "10");
      assertElementHasAttribute(text, "/petBucket/pet[1]", "class", Cat.class.getName());
      assertElementHasAttribute(text, "/petBucket/pet[2]", "class", Dog.class.getName());
   }
   
}
