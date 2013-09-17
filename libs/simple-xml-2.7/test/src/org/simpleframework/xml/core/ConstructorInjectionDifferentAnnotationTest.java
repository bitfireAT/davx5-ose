package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class ConstructorInjectionDifferentAnnotationTest extends ValidationTestCase {
   
   @Root
   private static class Example {
      @Element(name="k", data=true)
      private final String key;
      @Element(name="v", data=true, required=false)
      private final String value;
      public Example(@Element(name="k") String key, @Element(name="v") String value) {
         this.key = key;
         this.value = value;
      }
      public String getKey(){
         return key;
      }
      public String getValue(){
         return value;
      }
   }
   @Root
   private static class ExampleList {
      @ElementList(entry="e", data=true, inline=true)
      private final List<String> list;
      @Attribute(name="a", required=false)
      private final String name;
      public ExampleList(@ElementList(name="e") List<String> list, @Attribute(name="a") String name) {
         this.name = name;
         this.list = list;
      }
      public List<String> getList(){
         return list;
      }
      public String getName(){
         return name;
      }
   }
   @Root
   private static class InvalidExample {
      @Element(name="n", data=true, required=true)
      private final String name;
      @Attribute(name="v", required=false)
      private final String value;
      public InvalidExample(@Element(name="n") String name, @Element(name="v") String value) {
         this.name = name;
         this.value = value;;
      }
      public String getValue(){
         return value;
      }
      public String getName(){
         return name;
      }
   }
   public void testDifferentAnnotations() throws Exception{
      Persister persister = new Persister();
      Example example = new Example("a", "b");
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      String text = writer.toString();
      Example deserialized = persister.read(Example.class, text);
      assertEquals(deserialized.getKey(), "a");
      assertEquals(deserialized.getValue(), "b");
      validate(persister, deserialized);
   }
   
   public void testDifferentListAnnotations() throws Exception{
      Persister persister = new Persister();
      List<String> list = new ArrayList<String>();
      ExampleList example = new ExampleList(list, "a");
      list.add("1");
      list.add("2");
      list.add("3");
      list.add("4");
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      String text = writer.toString();
      ExampleList deserialized = persister.read(ExampleList.class, text);
      assertEquals(deserialized.getList().size(), 4);
      assertTrue(deserialized.getList().contains("1"));
      assertTrue(deserialized.getList().contains("2"));
      assertTrue(deserialized.getList().contains("3"));
      assertTrue(deserialized.getList().contains("4"));
      assertEquals(deserialized.getName(), "a");
      validate(persister, deserialized);
   }   
   
   public void testInvalidExample() throws Exception{
      Persister persister = new Persister();
      InvalidExample example = new InvalidExample("a", "b");
      StringWriter writer = new StringWriter();
      boolean exception = false;
      try {
         persister.write(example, writer);
      }catch(ConstructorException e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Non matching annotations should throw a constructor exception", exception);
   }
}
