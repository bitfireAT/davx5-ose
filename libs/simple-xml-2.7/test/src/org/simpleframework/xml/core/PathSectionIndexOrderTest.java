package org.simpleframework.xml.core;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.Order;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.ValidationTestCase;

public class PathSectionIndexOrderTest extends ValidationTestCase {
   
   @Order(
      elements={
         "./path[1]/first",
         "./path[3]/third",
         "./path[2]/second"
            
      },
      attributes={
         "./path[1]/first/@a",
         "./path[1]/first/@b"
      }
   )
   @Default
   @SuppressWarnings("all")
   private static class ElementPathSectionOutOfOrder {
      @Path("path[1]/first")
      private String one;
      @Path("path[2]/second")
      private String two;
      @Path("path[3]/third")
      private String three;
      @Attribute
      @Path("path[1]/first")
      private String a;
      @Attribute
      @Path("path[1]/first")
      private String b;
   }
   
   
   @Order(
      elements={
         "./path[1]/first/@value"
      }
   )
   @Default
   @SuppressWarnings("all")
   private static class AttributeReferenceInElementOrder {
      @Path("path[1]/first")
      private String value;
   }
   
   @Order(
         attributes={
            "./path[1]/first/value"
         }
      )
      @Default
      @SuppressWarnings("all")
      private static class ElementReferenceInAttributeOrder {
         @Attribute
         @Path("path[1]/first")         
         private String value;
      }
   
   
   public void testOutOfOrderElement() throws Exception {
      Persister persister = new Persister();
      ElementPathSectionOutOfOrder example = new ElementPathSectionOutOfOrder();
      boolean exception = false;
      try {
         persister.write(example, System.err);
      }catch(Exception e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Order should be illegal", exception);
   }
   
   public void testAttributeInElement() throws Exception {
      Persister persister = new Persister();
      AttributeReferenceInElementOrder example = new AttributeReferenceInElementOrder();
      boolean exception = false;
      try {
         persister.write(example, System.err);
      }catch(Exception e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Order should be illegal", exception);
   }
   
   public void testElementInAttribute() throws Exception {
      Persister persister = new Persister();
      ElementReferenceInAttributeOrder example = new ElementReferenceInAttributeOrder();
      boolean exception = false;
      try {
         persister.write(example, System.err);
      }catch(Exception e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Order should be illegal", exception);
   }
}
