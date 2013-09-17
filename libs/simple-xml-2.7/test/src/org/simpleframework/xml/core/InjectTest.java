package org.simpleframework.xml.core;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

public class InjectTest extends TestCase {
   
   private static final String SOURCE =
   "<example>"+
   "   <name>Some Name</name>"+
   "   <value>Some Value</value>"+
   "</example>";
   
   @Root
   private static class InjectExample {
      
      @Element
      private String name;
      
      @Element
      private String value;
      
      public String getName() {
         return name;
      }
      
      public String getValue() {
         return value;
      }
   }
   
   public void testInject() throws Exception {
      Persister persister = new Persister();
      InjectExample example = new InjectExample();
      
      persister.read(example, SOURCE);
      
      assertEquals(example.getName(), "Some Name");
      assertEquals(example.getValue(), "Some Value");
   }

}
