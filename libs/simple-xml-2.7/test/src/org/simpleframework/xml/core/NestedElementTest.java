package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;


public class NestedElementTest extends ValidationTestCase {

   @Root
   @SuppressWarnings("all")
   private static class NestedElementExample {
      @Attribute(name="area-code")
      @Path("contact-info[1]/phone")
      private final int areaCode;
      @Element
      @Path("contact-info[1]/address")
      private final String city;
      @Element
      @Path("contact-info[1]/address")
      private final String street;
      @Element
      @Path("contact-info[1]/phone")
      private final String mobile;
      @Element
      @Path("./contact-info[1]/phone")
      private final String home;
      public NestedElementExample(
              @Element(name="city") String city,
              @Element(name="street") String street,
              @Element(name="mobile") String mobile,
              @Element(name="home") String home,
              @Attribute(name="area-code") int areaCode) 
      {
         this.city=city;
         this.street=street;
         this.mobile=mobile;
         this.home=home;   
         this.areaCode = areaCode;
      }
            
   }
   public void testNestedExample() throws Exception{
      NestedElementExample example = new NestedElementExample("London", "Bevenden", "34512345", "63356464", 61);
      Persister persister = new Persister();
      StringWriter writer = new StringWriter();      
      persister.write(example, writer);      
      System.out.println(writer.toString());      
      NestedElementExample recovered = persister.read(NestedElementExample.class, writer.toString());
      assertEquals(example.city, recovered.city);
      assertEquals(example.street, recovered.street);
      assertEquals(example.mobile, recovered.mobile);
      assertEquals(example.home, recovered.home); 
      validate(example,persister);
   }

}
