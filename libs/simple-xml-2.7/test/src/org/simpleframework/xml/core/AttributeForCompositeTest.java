package org.simpleframework.xml.core;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

public class AttributeForCompositeTest extends TestCase {

   @Root
   private static class Example {
      @Attribute
      Map<String, String> map;
      public Example() {
         this.map = new HashMap<String, String>();
      }
   }
   
   public void testAttr() throws Exception {
      Example example = new Example();
      Persister persister = new Persister();
      boolean failure = false;
      
      try {
         persister.write(example, System.out);
      }catch(Exception e){
         failure = true;
         e.printStackTrace();
      }
      assertTrue("Should fail on bad attribute annotation", failure);
   }
}
