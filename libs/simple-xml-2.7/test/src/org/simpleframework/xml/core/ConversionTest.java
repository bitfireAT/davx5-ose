package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.strategy.Type;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.strategy.TreeStrategy;
import org.simpleframework.xml.strategy.Value;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeMap;
import org.simpleframework.xml.stream.OutputNode;
import org.simpleframework.xml.util.Dictionary;

public class ConversionTest extends ValidationTestCase {

   public static interface Converter<T> {
      public T read(InputNode node) throws Exception;
      public void write(OutputNode node, T value) throws Exception;
   }
   
   public static class Registry {
      private final Map<Class, Class> registry;
      public Registry() {
         this.registry = new HashMap<Class, Class>();
      }
      public Converter resolve(Class type) throws Exception{
         Class converter = registry.get(type);
         if(converter != null){
            return (Converter)converter.newInstance();
         }
         return null;
      }
      public void register(Class type, Class converter) {
         registry.put(type, converter);
      }
   }
   
   public static class Interceptor implements Strategy {
      private final Registry registry;
      private final Strategy strategy;
      public Interceptor(Strategy strategy, Registry registry){
         this.registry = registry;
         this.strategy = strategy;
      }
      public Value read(Type field, NodeMap<InputNode> node, Map map) throws Exception {
         Value value = strategy.read(field, node, map);
         Class type = value == null ? field.getType() : value.getType();
         Converter converter = registry.resolve(type);
         if(converter != null) {
            InputNode source = node.getNode();
            Object data = converter.read(source);
            return new Wrapper(value, data);
         }
         return value;
      }
      public boolean write(Type field, Object value, NodeMap<OutputNode> node, Map map) throws Exception {
         boolean reference = strategy.write(field, value, node, map);
         if(!reference) {
            Class type = value.getClass();
            Converter converter = registry.resolve(type);
            OutputNode source = node.getNode();
            if(converter != null) {
               converter.write(source, value);
               return true;
            }
            return false;
         }
         return reference;
      }
   }
   
   public static class Wrapper implements Value {
      private Value value;
      private Object data;
      public Wrapper(Value value, Object data){
         this.value = value;
         this.data = data;
      }
      public int getLength() {
         return value.getLength();
      }
      public Class getType() {
         return value.getType();
      }
      public Object getValue() {
         return data;
      }
      public boolean isReference() {
         return true;
      }
      public void setValue(Object data) {
         this.data = data;
      }
   }
   
   public static class Pet implements org.simpleframework.xml.util.Entry{
      private final String name;
      private final int age;
      public Pet(String name, int age){
         this.name = name;
         this.age = age;
      }
      public String getName() {
         return name;
      }
      public int getAge(){
         return age;
      }
   }
   
   public static class Cat extends Pet{
      public Cat(String name, int age) {
         super(name, age);
      }
   }
   
   public static class Dog extends Pet{
      public Dog(String name, int age) {
         super(name, age);
      }
   }
   
   /**
    * This will serialize a cat object into a custom XML format
    * without the use of annotations to transform the object.
    * It looks like the following.
    * <cat>
    *    <name>Kitty</name>
    *    <age>2</age>
    * </cat>
    */
   public static class CatConverter implements Converter<Cat>{
      private static final String ELEMENT_NAME = "name";
      private static final String ELEMENT_AGE = "age";
      public Cat read(InputNode source) throws Exception{
         int age = 0;
         String name = null;     
         while(true) {
            InputNode node = source.getNext();
            if(node == null) {
               break;
            }else if(node.getName().equals(ELEMENT_NAME)) {
               name = node.getValue();
            }else if(node.getName().equals(ELEMENT_AGE)){
               age = Integer.parseInt(node.getValue().trim());
            } 
         }
         return new Cat(name, age);
      }
      public void write(OutputNode node, Cat cat)throws Exception {
         OutputNode name = node.getChild(ELEMENT_NAME);
         name.setValue(cat.getName());
         OutputNode age = node.getChild(ELEMENT_AGE);
         age.setValue(String.valueOf(cat.getAge()));
      }
   }
   
   /**
    * This will serialize a dog into a custom XML format without
    * the need for annotations.
    * <dog name="Lassie" age="10"/>
    */
   public static class DogConverter implements Converter<Dog>{
      private static final String ELEMENT_NAME = "name";
      private static final String ELEMENT_AGE = "age";
      public Dog read(InputNode node) throws Exception{
         String name = node.getAttribute(ELEMENT_NAME).getValue();
         String age = node.getAttribute(ELEMENT_AGE).getValue();
         return new Dog(name, Integer.parseInt(age));
      }
      public void write(OutputNode node, Dog dog)throws Exception {
         node.setAttribute(ELEMENT_NAME, dog.getName());
         node.setAttribute(ELEMENT_AGE, String.valueOf(dog.getAge()));
      }      
   }
   
   @Root
   private static class PetShop {
      private @ElementList Dictionary<Pet> pets;
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
      Strategy strategy = new TreeStrategy();
      Strategy interceptor = new Interceptor(strategy, registry);
      Persister persister = new Persister(interceptor);
      StringWriter writer = new StringWriter();
      PetShop shop = new PetShop();
      
      registry.register(Dog.class, DogConverter.class);
      registry.register(Cat.class, CatConverter.class);
   
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
      
      assertElementHasAttribute(text, "/petShop/pets/pet", "name", "Lassie");
      assertElementHasAttribute(text, "/petShop/pets/pet", "age", "10");
      assertElementExists(text, "/petShop/pets/pet[2]/name");
      assertElementExists(text, "/petShop/pets/pet[2]/age");
   }
}
