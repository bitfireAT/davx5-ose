package org.simpleframework.xml.core;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class ConstructorInjectionDifferentTypeTest extends ValidationTestCase {

   @Root
   private static class Example {
      @Element(name="k", data=true)
      private final String key;
      @Element(name="v", data=true, required=false)
      private final String value;
      public Example(@Element(name="k") int key, @Element(name="v") String value) {
         this.key = String.valueOf(key);
         this.value = value;
      }
      public String getKey(){
         return key;
      }
      public String getValue(){
         return value;
      }
   }
   
   public void testDifferentTypes() throws Exception { 
      Persister persister = new Persister();
      Example example = new Example(1, "1");
      boolean exception = false;
      try {
         persister.write(example, System.out);
      }catch(ConstructorException e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Parameter matching should respect types", exception);
   }
}
