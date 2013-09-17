package org.simpleframework.xml.core;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class AnonymousClassTest extends ValidationTestCase {
   
   @Root(name="anonymous")
   private static class Anonymous {
      
      @Element
      @Namespace(prefix="prefix", reference="http://www.domain.com/reference")
      private static Object anonymous = new Object() {
         @Attribute(name="attribute")
         private static final String attribute = "example attribute"; 
         @Element(name="element")
         private static final String element = "example element";      
      };
   }
   /*
    TODO fix this test
   public void testAnonymousClass() throws Exception {
      Persister persister = new Persister();
      Anonymous anonymous = new Anonymous();
      
      validate(persister, anonymous);
   }
   */
   
   public void testA() {}

}
