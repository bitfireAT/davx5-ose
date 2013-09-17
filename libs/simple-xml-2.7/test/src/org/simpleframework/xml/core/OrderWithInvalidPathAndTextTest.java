package org.simpleframework.xml.core;

import org.simpleframework.xml.Order;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;

public class OrderWithInvalidPathAndTextTest extends ValidationTestCase {
  
   @Order(elements="a/b/c")
   public static class InvalidOrder {
      @Path("a/b")
      @Text
      private String text;
      public InvalidOrder(@Text String text) {
         this.text = text;
      }
   }
   
   public void testIvalidOrderWithText() throws Exception {
      Persister persister = new Persister();
      InvalidOrder order = new InvalidOrder("Some text");
      boolean failure = false;
      try {
         persister.write(order, System.out);
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("This is a real flakey test with an invalid order", failure);
   }

}
