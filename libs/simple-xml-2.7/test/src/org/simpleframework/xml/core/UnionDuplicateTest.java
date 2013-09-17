package org.simpleframework.xml.core;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementUnion;

public class UnionDuplicateTest extends ValidationTestCase {

   private static final String SOURCE =
   "<shapeExample>" +
   "  <circle>" +
   "    <type>CIRCLE</type>" +
   "  </circle>" +
   "  <square>" +
   "    <type>SQUARE</type>" +
   "  </square>" +
   "</shapeExample>";
   
   @Root
   public static class Square implements Shape {
      @Element
      private String type;
      
      public String type() {
         return type;
      }
   }
   @Root
   public static class Circle implements Shape {
      @Element
      private String type;
      private double radius;
      
      public double area() {
         return Math.PI * Math.pow(radius, 2.0);
      }
      
      public String type() {
         return type;
      }
   }
   public static interface Shape<T> {
      public String type();
   }
   @Root
   public static class Diagram {
      
      @ElementUnion({
         @Element(name="circle", type=Circle.class),
         @Element(name="square", type=Square.class)
      })
      private Shape shape;
      
      public Diagram() {
         super();
      }
      
      public void setShape(Shape shape){
         this.shape = shape;
      }
      
      public Shape getShape() {
         return shape;
      }
   }
   
   public void testShape() throws Exception {
      Persister persister = new Persister();
      boolean exception = false;
      try {
         Diagram example = persister.read(Diagram.class, SOURCE);
         assertNull(example);
      }catch(Exception e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Union can only appear once in source XML", exception);
   }
}
