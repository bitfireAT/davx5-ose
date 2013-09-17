package org.simpleframework.xml.core;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementUnion;

public class UnionMatchDepenencyTest extends ValidationTestCase {
      
   @Root
   private static class Example {
      @ElementUnion({
         @Element(name="x", type=Integer.class),
         @Element(name="y", type=Integer.class),
         @Element(name="z", type=Integer.class)         
      })
      private String value;
      public String getValue(){
         return value;
      }
      public void setValue(String value){
         this.value = value;
      }     
   }
   
   public void testTypeMatch() throws Exception {
      Persister persister = new Persister();
      Example example = new Example();
      example.setValue("a");
      boolean exception = false;
      try {
         persister.write(example, System.out);
      }catch(Exception e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Types must match for unions", exception);
   }
   

}
