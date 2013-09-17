package org.simpleframework.xml.convert;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.DefaultType;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.CycleStrategy;
import org.simpleframework.xml.stream.CamelCaseStyle;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;
import org.simpleframework.xml.stream.Style;

public class RegistryConverterCycleTest extends TestCase {

   public static class PersonConverter implements Converter<Person> {
      private final Serializer serializer;
      public PersonConverter(Serializer serializer) {
         this.serializer = serializer;
      }
      public Person read(InputNode node) throws Exception {
         return serializer.read(PersonDelegate.class, node.getNext());
      }
      public void write(OutputNode node, Person value) throws Exception {
         Person person = new PersonDelegate(value);
         serializer.write(person, node);
      }
      @Root
      @Default(DefaultType.PROPERTY)
      private static class PersonDelegate extends Person {
         public PersonDelegate() {
            super();
         }
         public PersonDelegate(Person person) {
            super(person.address, person.name, person.age);
         }
         public Address getAddress() {
            return address;
         }
         public void setAddress(Address address) {
            this.address = address;
         }
         public String getName() {
            return name;
         }
         public void setName(String name) {
            this.name = name;
         }
         public int getAge() {
            return age;
         }
         public void setAge(int age) {
            this.age = age;
         }
      }
   }

   @Root
   @Default
   public static class Club {
      private Address address;
      private List<Person> members;
      public Club() {
         super();
      }
      public Club(Address address) {
         this.members = new ArrayList<Person>();
         this.address = address;
      }
      public void addMember(Person person) {
         this.members.add(person);
      }
   }
   
   private static class Person {
      protected Address address;
      protected String name;
      protected int age;
      public Person() {
         super();
      }
      public Person(Address address, String name, int age) {
         this.address = address;
         this.name= name;
         this.age = age;
      }      
   }
   
   @Root
   @Default
   private static class Address {
      private String address;
      public Address() {
         super();
      }
      public Address(String address) {
         this.address = address;
      }
   }
   
   public void testCycle() throws Exception {
      Style style = new CamelCaseStyle();
      Format format = new Format(style);
      Registry registry = new Registry();
      Address address = new Address("An Address");
      Person person = new Person(address, "Niall", 30);
      CycleStrategy referencer = new CycleStrategy();
      RegistryStrategy strategy = new RegistryStrategy(registry, referencer);
      Serializer serializer = new Persister(strategy, format);
      Converter converter = new PersonConverter(serializer);
      Club club = new Club(address);
      
      club.addMember(person);
      registry.bind(Person.class, converter);
      
      serializer.write(club, System.out);
   }
}
