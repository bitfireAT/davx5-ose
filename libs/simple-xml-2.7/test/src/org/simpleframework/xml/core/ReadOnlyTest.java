package org.simpleframework.xml.core;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

public class ReadOnlyTest extends TestCase {
   
   private static final String SOURCE =
      "<example name='name'>"+
      "   <value>some text here</value>"+
      "</example>";
   
   @Root(name="example")
   private static class ReadOnlyFieldExample {
      
      @Attribute(name="name") private final String name;
      @Element(name="value") private final String value;
      
      public ReadOnlyFieldExample(@Attribute(name="name") String name, @Element(name="value") String value) {
         this.name = name;
         this.value = value;
      }
   }
   
   @Root(name="example")
   private static class ReadOnlyMethodExample {
      
      private final String name;
      private final String value;
      
      public ReadOnlyMethodExample(@Attribute(name="name") String name, @Element(name="value") String value) {
         this.name = name;
         this.value = value;
      }
      
      @Attribute(name="name")
      public String getName() {
         return name;
      }
      
      @Element(name="value")
      public String getValue() {
         return value;
      }
   }
   
   @Root(name="example")
   private static class IllegalReadOnlyMethodExample {
      
      private final String name;
      private final String value;

      public IllegalReadOnlyMethodExample(@Attribute(name="name") String name, @Element(name="value") String value) {
         this.name = name;
         this.value = value;
      }
      
      @Attribute(name="name")
      public String getName() {
         return name;
      }
      
      @Element(name="value")
      public String getValue() {
         return value;
      }
      
      @Element(name="illegal")
      public String getIllegalValue() {
         return value;
      }
   }
   
   public void testReadOnlyField() throws Exception {
      Persister persister = new Persister();
      ReadOnlyFieldExample example = persister.read(ReadOnlyFieldExample.class, SOURCE);
      
      assertEquals(example.name, "name");
      assertEquals(example.value, "some text here");
   }
   
   public void testReadOnlyMethod() throws Exception {
      Persister persister = new Persister();
      ReadOnlyMethodExample example = persister.read(ReadOnlyMethodExample.class, SOURCE);
      
      assertEquals(example.getName(), "name");
      assertEquals(example.getValue(), "some text here");
   }

   public void testIllegalReadOnlyMethod() throws Exception {
      boolean failure = false;
      try {
         Persister persister = new Persister();
         IllegalReadOnlyMethodExample example = persister.read(IllegalReadOnlyMethodExample.class, SOURCE);
      }catch(Exception e) {
         failure = true;
      }
      assertTrue(failure);
   }
}
