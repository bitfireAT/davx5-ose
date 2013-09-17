package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Order;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.ValidationTestCase;

public class PathWithMixedOrdreringTest extends ValidationTestCase {
   
   @Default
   @Order(elements={"name[1]/first", "name[1]/surname", "age/date", "name[2]/nickname"})
   private static class Person {      
      @Path("name[1]")
      private String first;
      @Path("name[1]")
      private String surname;
      @Path("name[2]")
      private String nickname;
      @Path("age")
      private String date;
      public String getFirst() {
         return first;
      }
      public void setFirst(String first) {
         this.first = first;
      }
      public String getSurname() {
         return surname;
      }
      public void setSurname(String surname) {
         this.surname = surname;
      }
      public String getNickname() {
         return nickname;
      }
      public void setNickname(String nickname) {
         this.nickname = nickname;
      }
      public String getDate() {
         return date;
      }
      public void setDate(String date) {
         this.date = date;
      }
   }   
   
   public void testMixedOrdering() throws Exception {
      Persister persister = new Persister();
      StringWriter writer = new StringWriter();
      Person person = new Person();
      person.setFirst("Jack");
      person.setSurname("Daniels");
      person.setNickname("JD");
      person.setDate("19/01/1912");
      persister.write(person, writer);
      Person recovered = persister.read(Person.class, writer.toString());
      assertEquals(recovered.getFirst(), person.getFirst());
      assertEquals(recovered.getSurname(), person.getSurname());
      assertEquals(recovered.getNickname(), person.getNickname());
      assertEquals(recovered.getDate(), person.getDate());
      validate(person, persister);
      assertElementHasValue(writer.toString(), "/person/name[1]/first", "Jack");
      assertElementHasValue(writer.toString(), "/person/name[1]/surname", "Daniels");
      assertElementHasValue(writer.toString(), "/person/name[2]/nickname", "JD");
      assertElementHasValue(writer.toString(), "/person/age[1]/date", "19/01/1912");
   }


}
