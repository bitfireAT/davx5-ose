package org.simpleframework.xml.core;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

public class ConstructorInjectionWithByteCodeTest extends TestCase {
   
   private static final String SOURCE =
   "<exampleByteCode age='30'>\n"+
   "   <name>John Doe</name>\n"+
   "</exampleByteCode>";
   
   @Root
   public static class ExampleByteCode {
      @Element
      private String name;
      @Attribute
      private int age;
      public ExampleByteCode(@Element String name, @Attribute int age) {
         this.name = name;
         this.age = age;
      }
      public String getName() {
         return name;
      }
      public int getAge() {
         return age;
      }
   }
   
   public void testByteCode() throws Exception {
     // Persister persister = new Persister();
     // ExampleByteCode example = persister.read(ExampleByteCode.class, SOURCE);
      
     // assertEquals(example.getName(), "John Doe");
     // assertEquals(example.getAge(), 30);
   }

}
