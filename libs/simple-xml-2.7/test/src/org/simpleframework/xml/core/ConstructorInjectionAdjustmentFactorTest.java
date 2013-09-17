package org.simpleframework.xml.core;

import junit.framework.TestCase;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Transient;

public class ConstructorInjectionAdjustmentFactorTest extends TestCase {
   
   private static final String SOURCE =
      "<test>\n"+
      "   <a>Value for A</a>\n"+
      "   <d>Value for D</d>\n"+
      "   <c>Value for C</c>\n"+
      "   <b>Value for B</b>\n"+   
      "</test>\n";
   
   @Default
   private static class ConstructorAdjustmentExample {      
      private final String a;
      private final String b;
      private String c;
      private String d;
      @Transient
      private boolean longestUsed;
      @SuppressWarnings("unused")
      public ConstructorAdjustmentExample(
            @Element(name="a") String a,
            @Element(name="b") String b)
      {
         this.a = a;
         this.b = b;
      }
      @SuppressWarnings("unused")
      public ConstructorAdjustmentExample(
            @Element(name="a") String a,
            @Element(name="b") String b, 
            @Element(name="c") String c,
            @Element(name="d") String d)
      {
         this.a = a;
         this.b = b;
         this.c = c;
         this.d = d;
         this.longestUsed = true;
      }      
   }

   public void testAdjustmentFactor() throws Exception {
      Persister persister = new Persister();
      ConstructorAdjustmentExample example = persister.read(ConstructorAdjustmentExample.class, SOURCE);
      assertEquals(example.a, "Value for A");
      assertEquals(example.b, "Value for B");
      assertEquals(example.c, "Value for C");
      assertEquals(example.d, "Value for D");
      assertTrue("Longest constructor should be used", example.longestUsed);
   }
}
