package org.simpleframework.xml.convert;

import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Root;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

public class ExampleConverters {
   
   
   public static class CowConverter implements Converter<Cow> {
      
      public Cow read(InputNode node) throws Exception {
         String name = node.getAttribute("name").getValue();
         String age = node.getAttribute("age").getValue();
         return new Cow(name, Integer.parseInt(age));
      }
      
      public void write(OutputNode node, Cow cow) throws Exception {
         node.setAttribute("name", cow.getName());
         node.setAttribute("age", String.valueOf(cow.getAge()));
         node.setAttribute("legs", String.valueOf(cow.getLegs()));
      }
   }
   
   public static class ChickenConverter implements Converter<Chicken> {
      
      public Chicken read(InputNode node) throws Exception {
         String name = node.getAttribute("name").getValue();
         String age = node.getAttribute("age").getValue();
         return new Chicken(name, Integer.parseInt(age));
      }
      
      public void write(OutputNode node, Chicken chicken) throws Exception {
         node.setAttribute("name", chicken.getName());
         node.setAttribute("age", String.valueOf(chicken.getAge()));
         node.setAttribute("legs", String.valueOf(chicken.getLegs()));
      }
   }
   
   public static class Animal {
      private final String name;
      private final int age;
      private final int legs;
      public Animal(String name, int age, int legs) {
         this.name = name;
         this.legs = legs;
         this.age = age;
      }
      public String getName() {
         return name;
      }
      public int getAge(){
         return age;
      }      
      public int getLegs() {
         return legs;
      }
   }
   
   public static class Chicken extends Animal {
      public Chicken(String name, int age) {
         super(name, age, 2);
      }
   }
   
   public static class Cow extends Animal {
      public Cow(String name, int age) {
         super(name, age, 4);
      }
   }

   @Root
   @Convert(EntryConverter.class)
   public static class Entry {
      private final String name;
      private final String value;
      public Entry(String name, String value) {
         this.name = name;
         this.value = value;
      }
      public String getName() {
         return name;
      }
      public String getValue() {
         return value;
      }
      public boolean equals(Object value){
         if(value instanceof Entry) {
            return equals((Entry)value);
         }
         return false;
      }
      public boolean equals(Entry entry) {
         return entry.name.equals(name) &&
                entry.value.equals(value);
      }
   }
   
   @Convert(ExtendedEntryConverter.class) 
   public static class ExtendedEntry extends Entry {
      private final int code;
      public ExtendedEntry(String name, String value, int code) {
         super(name, value);
         this.code = code;
      }
      public int getCode() {
         return code;
      }
   }
   
   public static class ExtendedEntryConverter implements Converter<ExtendedEntry> {
      public ExtendedEntry read(InputNode node) throws Exception {
         String name = node.getAttribute("name").getValue();
         String value = node.getAttribute("value").getValue();
         String code = node.getAttribute("code").getValue();
         return new ExtendedEntry(name, value, Integer.parseInt(code));
      }
      public void write(OutputNode node, ExtendedEntry entry) throws Exception {
         node.setAttribute("name", entry.getName());
         node.setAttribute("value", entry.getValue());
         node.setAttribute("code", String.valueOf(entry.getCode()));
      }
   }
   
   public static class OtherEntryConverter implements Converter<Entry> {
      public Entry read(InputNode node) throws Exception {
         String name = node.getAttribute("name").getValue();
         String value = node.getAttribute("value").getValue();
         return new Entry(name, value);
      }
      public void write(OutputNode node, Entry entry) throws Exception {
         node.setAttribute("name", entry.getName());
         node.setAttribute("value", entry.getValue());
      }
   }
   
   public static class EntryConverter implements Converter<Entry> {
      public Entry read(InputNode node) throws Exception {
         String name = node.getNext("name").getValue();
         String value = node.getNext("value").getValue();
         return new Entry(name, value);
      }
      public void write(OutputNode node, Entry entry) throws Exception {
         node.getChild("name").setValue(entry.getName());
         node.getChild("value").setValue(entry.getValue());
      }
   }
   
   public static class EntryListConverter implements Converter<List<Entry>> {
      private OtherEntryConverter converter = new OtherEntryConverter();
      public List<Entry> read(InputNode node) throws Exception {
         List<Entry> entryList = new ArrayList<Entry>();
         while(true) {
            InputNode item = node.getNext("entry");
            if(item == null) {
               break;
            }
            entryList.add(converter.read(item));
         }
         return entryList;
      }
      public void write(OutputNode node, List<Entry> entryList) throws Exception {
         for(Entry entry : entryList) {
            OutputNode item = node.getChild("entry");
            converter.write(item, entry);
         }
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
      public boolean equals(Object value) {
         if(value instanceof Pet) {
            return equals((Pet)value);
         }
         return false;
      }
      public boolean equals(Pet pet) {
         return pet.name.equals(name) &&
                pet.age == age;
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
}
