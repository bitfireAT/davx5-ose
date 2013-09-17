package org.simpleframework.xml.core;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementUnion;

public class ConstructorInjectionWithUnionTest extends TestCase {
   
   private static final String TEXT =
   "<unionExample>" +
   "  <x/>"+
   "</unionExample>";
   
   public static class UnionExample {
      @ElementUnion({
         @Element(name="x", type=X.class),
         @Element(name="y", type=Y.class),
         @Element(name="z", type=X.class)
      })
      private final X value;
      
      public UnionExample(@Element(name="x") X value) {
         this.value = value;
      }
      
      public UnionExample(@Element(name="y") Y value) {
         this.value = value;
      }
   }
   
   public static class X{}
   public static class Y extends X{}
   public static class Z extends Y{}
   
   public void testInjection() throws Exception {
      Persister persister = new Persister();
      UnionExample example = persister.read(UnionExample.class, TEXT);
      
      assertNotNull(example.value);
   }
}
