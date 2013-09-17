package org.simpleframework.xml.core;

import java.util.List;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;

@SuppressWarnings("all")
public class ScannerCreatorTest extends TestCase {

   private static class Example1{
      @Path("path")
      @Element(name="a")
      private String el;
      @Attribute(name="a")
      private String attr;
      public Example1(
            @Element(name="a") String el, 
            @Attribute(name="a") String attr) {}
   }
   
   public void testScanner() throws Exception {
      Scanner scanner = new ObjectScanner(new DetailScanner(Example1.class), new Support());
      Instantiator creator  = scanner.getInstantiator();
      List<Creator> list = creator.getCreators();
      System.err.println(list.get(0));
   }
}
