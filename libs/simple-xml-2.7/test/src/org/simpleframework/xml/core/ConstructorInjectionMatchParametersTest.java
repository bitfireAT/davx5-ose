package org.simpleframework.xml.core;

import java.io.StringWriter;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

public class ConstructorInjectionMatchParametersTest extends TestCase {

   @Root
   private static class Example {
      @Attribute
      private String name;
      @Element
      private String value;
      @Attribute(required=false)
      private Integer type;
      
      public Example(
            @Attribute(name="name") String name,
            @Element(name="value") String value,
            @Attribute(name="type") Integer type)
      {
         this.name = name;
         this.value = value;
         this.type = type;
      }      
   }
   
   public void testMatch() throws Exception {
      Persister persister = new Persister();
      Example example = new Example("a", "A", 10);
      StringWriter writer = new StringWriter();
      
      persister.write(example, writer);
      
      Example recovered = persister.read(Example.class, writer.toString());
      
      assertEquals(recovered.name, example.name);
      assertEquals(recovered.value, example.value);
      assertEquals(recovered.type, example.type);
   }
   
   public void testMatchWithNull() throws Exception {
      Persister persister = new Persister();
      Example example = new Example("a", "A", null);
      StringWriter writer = new StringWriter();
      
      persister.write(example, writer);
      
      Example recovered = persister.read(Example.class, writer.toString());
      
      assertEquals(recovered.name, example.name);
      assertEquals(recovered.value, example.value);
      assertEquals(recovered.type, example.type);
   }
}
