package org.simpleframework.xml.core;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;

public class HierarchyTest extends ValidationTestCase {
   
   public static class Basic {
      
      @Element
      private String a;
      
      @Element
      private String b;
      
      private long one;
      
      private Basic() {
         super();
      }
      
      public Basic(long one, String a, String b) {
         this.one = one;
         this.a = a;
         this.b = b;
      }
      
      @Element
      public long getOne() {
         return one;
      }
      
      @Element
      public void setOne(long one) {
         this.one = one;
      }
   }
   
   public static class Abstract extends Basic {
      
      @Element
      private int c;
      
      private Abstract() {
         super();
      }
      
      public Abstract(long one, String a, String b, int c) {
         super(one, a, b);
         this.c = c;
      }
   }
   
   public static class Specialized extends Abstract {
      
      @Element
      private int d;
      
      private double two;
      
      private Specialized() {
         super();
      }
      
      public Specialized(long one, double two, String a, String b, int c, int d) {
         super(one, a, b, c);
         this.two = two;
         this.d = d;
      }
      
      @Element
      public double getTwo() {
         return two;
      }
      
      @Element
      public void setTwo(double two) {
         this.two = two;
      }
   }
   
   public void testHierarchy() throws Exception {
      Serializer serializer = new Persister();
      Specialized special = new Specialized(1L, 2.0, "a", "b", 1, 2);
      
      validate(special, serializer);
   }

}
