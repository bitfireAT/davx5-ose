package org.simpleframework.xml.core;

import java.util.Collections;
import java.util.List;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class UnionRequiredTest extends ValidationTestCase {

   @Root
   public static class Example {
      
      @ElementUnion({
         @Element(name="double", type=Double.class, required=false),
         @Element(name="string", type=String.class, required=true),
         @Element(name="int", type=Integer.class, required=true)
      })
      private Object value;
   }
   
   @Root
   public static class ExampleList {
      
      @ElementListUnion({
         @ElementList(name="double", type=Double.class, required=false),
         @ElementList(name="string", type=String.class, required=true),
         @ElementList(name="int", type=Integer.class, required=true)
      })
      private List<Object> value;
   }
   
   public void testRequired() throws Exception {
      Persister persister = new Persister();
      Example example = new Example();
      example.value = "test";
      boolean failure = false;
      try {
         persister.write(example, System.out);
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Requirement did not match", failure);
   }
   
   public void testListRequired() throws Exception {
      Persister persister = new Persister();
      ExampleList example = new ExampleList();
      example.value = Collections.singletonList((Object)"test");
      boolean failure = false;
      try {
         persister.write(example, System.out);
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Requirement did not match", failure); 
   }
}
