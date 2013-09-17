package org.simpleframework.xml.core;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.DefaultType;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Transient;

public class ScannerDefaultTest extends TestCase {

   @Root
   @Default(DefaultType.FIELD)
   private static class OrderItem {
      private Customer customer;
      private String name;
      @Transient
      private double price; // should be transient to avoid having prices as an attribute and an element, which is legal
      @Attribute
      private double getPrice() {
         return price;
      }
      @Attribute
      private void setPrice(double price) {
         this.price = price;
      }
   }
   
   @Root
   @Default(DefaultType.PROPERTY)
   private static class Customer {
      private String name;
      private String getName() {
         return name;
      }
      public void setName(String name){
         this.name = name;
      }
   }
   
   @Root
   @Default(DefaultType.FIELD)
   private static class DuplicateExample {
      private int id;
      @Attribute
      public int getId() {
         return id;
      }
      @Attribute
      public void setId(int id){
         this.id = id;
      }
   }
   
   @Root
   @Default(DefaultType.PROPERTY)
   private static class NonMatchingAnnotationExample {
      private String name;
      private String getName() {
         return name;
      }
      @Attribute
      public void setName(String name) {
         this.name = name;
      }
   }
   
   public void testNonMatchingAnnotationExample() throws Exception {
      boolean failure = false;
      
      try {
         new ObjectScanner(new DetailScanner(NonMatchingAnnotationExample.class), new Support());
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Failure should occur when annotations do not match", failure);
   }
   
   public void testDuplicateExample() throws Exception {
      Scanner scanner = new ObjectScanner(new DetailScanner(DuplicateExample.class), new Support());
      LabelMap attributes = scanner.getSection().getAttributes();
      LabelMap elements = scanner.getSection().getElements();  

      assertEquals(attributes.get("id").getType(), int.class);
      assertEquals(elements.get("id").getType(), int.class);
   }
   
   public void testScanner() throws Exception {
      Scanner scanner = new ObjectScanner(new DetailScanner(OrderItem.class), new Support());
      LabelMap attributes = scanner.getSection().getAttributes();
      LabelMap elements = scanner.getSection().getElements();
      
      assertEquals(attributes.get("price").getType(), double.class);
      assertEquals(elements.get("customer").getType(), Customer.class);
   }
}
