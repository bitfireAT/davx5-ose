package org.simpleframework.xml.core;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.Order;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.ValidationTestCase;

public class PathAttributeWithNoExistingSectionTest extends ValidationTestCase {
   
   @Order(attributes={"some/path/to/build/@value"})
   @Default
   private static class AttributeWithNoExistingSectionExample {
      @Attribute
      @Path("some/path/to/build")
      private String value;
      public void setValue(String value){
         this.value = value;
      }
   }
   
   public void testOrder() throws Exception {
      Persister persister = new Persister();
      AttributeWithNoExistingSectionExample example = new AttributeWithNoExistingSectionExample();
      example.setValue("Attribute value");
      persister.write(example, System.err);
      validate(example, persister);
      
   }

}
