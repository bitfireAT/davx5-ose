package org.simpleframework.xml.core;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;

public class AttributeNameSameAsElementNameTest extends TestCase{
   
   @Root
   private static class AttributeSameAsElement{
      @Attribute(name="a")
      private String attr;
      @Element(name="a")
      private String elem;
      public void setAttr(String attr) {
         this.attr = attr;
      }
      public void setElem(String elem) {
         this.elem = elem;
      }
      public AttributeSameAsElement(
            @Element(name="a") String name,
            @Attribute(name="a") String elem) {
         this.attr = attr;
         this.elem = elem;
      }
   }
   public void testSame() throws Exception {
      Persister persister = new Persister();
      AttributeSameAsElement element = new AttributeSameAsElement("attr", "elem");
      boolean exception = false;
      try {
         persister.write(element, System.err);
      }catch(Exception e){
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Can not have similarly named values in constructor", exception);
   }
   

}
