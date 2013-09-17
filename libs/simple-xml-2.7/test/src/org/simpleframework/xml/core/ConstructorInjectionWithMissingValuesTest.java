package org.simpleframework.xml.core;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

public class ConstructorInjectionWithMissingValuesTest extends TestCase{

   private static final String MATCH_A =
   "<test>\n"+
   "   <a>Value for A</a>\n"+
   "</test>\n";
   
   private static final String MATCH_A_D =
   "<test>\n"+
   "   <a>Value for A</a>\n"+
   "   <d>55</d>\n"+
   "</test>\n";
   
   private static final String MATCH_A_C_D =
   "<test>\n"+
   "   <a>Value for A</a>\n"+
   "   <d>55</d>\n"+
   "   <c>Value for C</c>\n"+
   "</test>\n";
   
   private static final String MATCH_A_B_C_D =
   "<test>\n"+
   "   <a>Value for A</a>\n"+
   "   <d>55</d>\n"+
   "   <c>Value for C</c>\n"+
   "   <b>Value for B</b>\n"+   
   "</test>\n";
   
   @Root(name="test")
   private static class ConstructorWithMissingValues{
      @Element(required=false)
      private final String a;
      @Element(required=false)
      private final String b;
      @Element(required=false)
      private final String c;
      @SuppressWarnings("unused")
      public ConstructorWithMissingValues(
         @Element(name="a", required=false) String a,
         @Element(name="b", required=false) String b,
         @Element(name="c", required=false) String c)
      {
         this.a = a;
         this.b = b;
         this.c = c;
      }
      public String getA() {
         return a;
      }
      public String getB() {
         return b;
      }
      public String getC() {
         return c;
      }
   }
   
   @Root(name="test")
   private static class ManyConstructorsWithMissingValues{
      @Element(required=false)
      private final String a;
      @Element(required=false)
      private final String b;
      @Element(required=false)
      private final String c; 
      @Element
      private final int d;    
      @SuppressWarnings("unused")
      public ManyConstructorsWithMissingValues(
            @Element(name="a", required=false) String a)
      {
         this.a = a;
         this.b = "Default B";
         this.c = "Default C";
         this.d = 10;
      }
      @SuppressWarnings("unused")
      public ManyConstructorsWithMissingValues(
         @Element(name="a", required=false) String a,
         @Element(name="b", required=false) String b)
      {
         this.a = a;
         this.b = b;
         this.c = "Default C";
         this.d = 10;
      }
      @SuppressWarnings("unused")
      public ManyConstructorsWithMissingValues(
         @Element(name="a", required=false) String a,
         @Element(name="d") int d)
      {
         this.a = a;
         this.b = "Default B";
         this.c = "Default C";
         this.d = d;
      }
      @SuppressWarnings("unused")
      public ManyConstructorsWithMissingValues(
         @Element(name="a", required=false) String a,
         @Element(name="b", required=false) String b,
         @Element(name="c", required=false) String c)
      {
         this.a = a;
         this.b = b;
         this.c = c;
         this.d = 10;
      }
      @SuppressWarnings("unused")
      public ManyConstructorsWithMissingValues(
         @Element(name="a", required=false) String a,
         @Element(name="b", required=false) String b,
         @Element(name="c", required=false) String c,
         @Element(name="d") int d)
      {
         this.a = a;
         this.b = b;
         this.c = c;
         this.d = d;
      }
      public String getA() {
         return a;
      }
      public String getB() {
         return b;
      }
      public String getC() {
         return c;
      }
      public int getD(){
         return d;
      }
   }
   
   public void testDefaultMatchA() throws Exception {
      Persister persister = new Persister();
      ConstructorWithMissingValues example = persister.read(ConstructorWithMissingValues.class, MATCH_A);
      assertEquals(example.getA(), "Value for A");
      assertEquals(example.getB(), null);
      assertEquals(example.getC(), null);
   }
   
   public void testMatchAD() throws Exception {
      Persister persister = new Persister();
      ManyConstructorsWithMissingValues example = persister.read(ManyConstructorsWithMissingValues.class, MATCH_A_D);
      assertEquals(example.getA(), "Value for A");
      assertEquals(example.getB(), "Default B");
      assertEquals(example.getC(), "Default C");
      assertEquals(example.getD(), 55);
   }
   
   public void testMatchACD() throws Exception {
      Persister persister = new Persister();
      ManyConstructorsWithMissingValues example = persister.read(ManyConstructorsWithMissingValues.class, MATCH_A_C_D);
      assertEquals(example.getA(), "Value for A");
      assertEquals(example.getB(), null);
      assertEquals(example.getC(), "Value for C");
      assertEquals(example.getD(), 55);
   }
   
   public void testMatchABCD() throws Exception {
      Persister persister = new Persister();
      ManyConstructorsWithMissingValues example = persister.read(ManyConstructorsWithMissingValues.class, MATCH_A_B_C_D);
      assertEquals(example.getA(), "Value for A");
      assertEquals(example.getB(), "Value for B");
      assertEquals(example.getC(), "Value for C");
      assertEquals(example.getD(), 55);
   }
}
