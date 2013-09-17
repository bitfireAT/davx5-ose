package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class UnionConstructorInjectionTest extends ValidationTestCase {

   @Root
   private static class Example {
      @ElementUnion({
         @Element(name="login"),
         @Element(name="account"),
         @Element(name="username"),
         @Element(name="id"),
         @Element(name="name")
      })
      private final String name;
      
      @ElementUnion({
         @Element(name="password"),
         @Element(name="value")
      })
      private String password;
      
      @Attribute
      private int age;
      
      public Example(@Element(name="id") String name){
         this.name = name;
      }
      
      public int getAge(){
         return age;
      }
      
      public void setAge(int age){
         this.age = age;
      }
      
      public void setPassword(String password){
         this.password = password;
      }
      
      public String getPassword(){
         return password;
      }
      
      public String getName(){
         return name;
      }
   }
   
   @Root
   private static class InvalidExample {
      @ElementUnion({
         @Element(name="login"),
         @Element(name="account"),
         @Element(name="username"),
         @Element(name="id"),
         @Element(name="name")
      })
      private String name;
      
      public InvalidExample(@Element(name="id") int name){
         this.name = String.valueOf(name);
      }
      public String getName(){
         return name;
      }
   }
   
   @Root
   private static class InvalidAnnotationExample {
      @ElementUnion({
         @Element(name="login"),
         @Element(name="account"),
         @Element(name="username"),
         @Element(name="id"),
         @Element(name="name")
      })
      private String name;
      
      public InvalidAnnotationExample(@Attribute(name="id") String name){
         this.name = name;
      }
      public String getName(){
         return name;
      }
   }
   
   public void testConstructorInjection() throws Exception{
      Persister persister = new Persister();
      Example example = new Example("john.doe");
      example.setPassword("password123");
      example.setAge(20);
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      String text = writer.toString();
      Example deserialized = persister.read(Example.class, text);
      assertEquals(deserialized.getName(), "john.doe");
      validate(persister, example);
      assertElementExists(text, "/example/login");
      assertElementHasValue(text, "/example/login", "john.doe");
      assertElementExists(text, "/example/password");
      assertElementHasValue(text, "/example/password", "password123");
      assertElementHasAttribute(text, "/example", "age", "20"); 
   }

   public void testInvalidConstructorInjection() throws Exception{
      Persister persister = new Persister();
      InvalidExample example = new InvalidExample(10);
      StringWriter writer = new StringWriter();
      boolean exception = false;
      try {
         persister.write(example, writer);
      }catch(ConstructorException e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Type should be respected in union constructors", exception);
   }
      
   public void testInvalidAnnotationConstructorInjection() throws Exception{
      Persister persister = new Persister();
      InvalidAnnotationExample example = new InvalidAnnotationExample("john.doe");
      StringWriter writer = new StringWriter();
      boolean exception = false;
      try {
         persister.write(example, writer);
      }catch(ConstructorException e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Annotation type should be respected in union constructors", exception);
   }
}
