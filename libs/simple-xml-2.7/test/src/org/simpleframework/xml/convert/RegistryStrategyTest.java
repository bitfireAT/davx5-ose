package org.simpleframework.xml.convert;

import java.io.StringWriter;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.convert.ExampleConverters.Cat;
import org.simpleframework.xml.convert.ExampleConverters.CatConverter;
import org.simpleframework.xml.convert.ExampleConverters.Dog;
import org.simpleframework.xml.convert.ExampleConverters.DogConverter;
import org.simpleframework.xml.convert.ExampleConverters.Pet;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.util.Dictionary;

public class RegistryStrategyTest extends ValidationTestCase {
   
   @Root
   @Namespace(prefix="a", reference="http://domain/a")
   private static class PetShop {
      @ElementList
      @Namespace(prefix="b", reference="http://domain/b")
      private Dictionary<Pet> pets;
      public PetShop(){
         this.pets = new Dictionary<Pet>();
      }
      public void addPet(Pet pet) {
         pets.add(pet);
      }
      public Pet getPet(String name){
         return pets.get(name);
      }
   }
   
   public void testConverter() throws Exception {
      Registry registry = new Registry();
      Strategy interceptor = new RegistryStrategy(registry);
      Persister persister = new Persister(interceptor);
      StringWriter writer = new StringWriter();
      PetShop shop = new PetShop();
      
      registry.bind(Dog.class, DogConverter.class)
              .bind(Cat.class, CatConverter.class);
   
      shop.addPet(new Dog("Lassie", 10));
      shop.addPet(new Cat("Kitty", 2));
      
      persister.write(shop, writer);
      persister.write(shop, System.out);
      
      String text = writer.toString();
      PetShop newShop = persister.read(PetShop.class, text);
      
      assertEquals("Lassie", newShop.getPet("Lassie").getName());
      assertEquals(10, newShop.getPet("Lassie").getAge());
      assertEquals("Kitty", newShop.getPet("Kitty").getName());
      assertEquals(2, newShop.getPet("Kitty").getAge());
      
      assertElementExists(text, "/petShop");
      assertElementExists(text, "/petShop/pets");
      assertElementExists(text, "/petShop/pets/pet[1]");
      assertElementExists(text, "/petShop/pets/pet[2]");
      assertElementDoesNotExist(text, "/petShop/pets/pet[3]");
      assertElementHasNamespace(text, "/petShop", "http://domain/a");
      assertElementHasNamespace(text, "/petShop/pets", "http://domain/b");
      assertElementHasNamespace(text, "/petShop/pets/pet[1]", null);
      assertElementHasAttribute(text, "/petShop/pets/pet[1]", "name", "Lassie");
      assertElementHasAttribute(text, "/petShop/pets/pet[1]", "age", "10");
      assertElementHasValue(text, "/petShop/pets/pet[2]/name", "Kitty");
      assertElementHasValue(text, "/petShop/pets/pet[2]/age", "2");
   }
}
