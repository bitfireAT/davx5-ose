package org.simpleframework.xml.core;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Order;
import org.simpleframework.xml.Transient;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.AttributeException;
import org.simpleframework.xml.core.ElementException;
import org.simpleframework.xml.core.Persister;

public class RedundantOrderTest extends ValidationTestCase {

   @Order(elements={"a", "b", "c"})
   public static class ElementEntry {
      
      @Element
      private String a;
      
      @Element
      private String b;
      
      @Transient
      private String c;
      
      private ElementEntry() {
         super();
      }
      
      public ElementEntry(String a, String b, String c) {
         this.a = a;
         this.b = b;
         this.c = c;
      }
   }
   

   @Order(attributes={"a", "b", "c"})
   public static class AttributeEntry {
      
      @Attribute
      private String a;
      
      @Attribute
      private String b;
      
      @Transient
      private String c;
      
      private AttributeEntry() {
         super();
      }
      
      public AttributeEntry(String a, String b, String c) {
         this.a = a;
         this.b = b;
         this.c = c;
      }
   }
   
   public void testRedundantElementOrder() throws Exception {
      ElementEntry entry = new ElementEntry("a", "b", "c");
      Persister persister = new Persister();
      boolean exception = false;
      
      try {
         validate(entry, persister);
      }catch(ElementException e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue(exception);
   }
   
   public void testRedundantAttributeOrder() throws Exception {
      AttributeEntry entry = new AttributeEntry("a", "b", "c");
      Persister persister = new Persister();
      boolean exception = false;
      
      try {
         validate(entry, persister);
      }catch(AttributeException e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue(exception);
   }
}
