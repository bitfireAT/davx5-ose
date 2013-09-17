package org.simpleframework.xml.core;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class NonFinalConstructorInjectionTest extends ValidationTestCase {
   
   @Root
   private static class NonFinalExample {
      @Element
      private String name;
      @Element
      private String value;
      public NonFinalExample(@Element(name="name") String name, @Element(name="value") String value) {
         this.name = name;
         this.value = value;
      }
      public String getName(){
         return name;
      }
      public String getValue(){
         return value;
      }
   }
   
   public void testNonFinal() throws Exception {
      Persister persister = new Persister();
      NonFinalExample example = new NonFinalExample("A", "a");
      
      validate(example, persister);
   }
}
