package org.simpleframework.xml.core;

import java.io.StringWriter;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.stream.CamelCaseStyle;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.Style;

public class PathCaseTest extends TestCase {

   @Root
   public static class CaseExample {
      @Path("SomePath[1]")
      @Element
      private final String a;
      @Path("somePath[1]")
      @Element
      private final String b;
      public CaseExample(
            @Path("SomePath[1]") @Element(name="a") String a, 
            @Path("somePath[1]") @Element(name="b") String b) {
         this.a = a;
         this.b = b;
      }
   }
   
   @Root
   public static class CollisionExample {
      @Path("SomePath[1]")
      @Element(name="a")
      private final String a;
      @Path("somePath[1]")
      @Element(name="a")
      private final String b;
      public CollisionExample(
            @Path("SomePath[1]") @Element(name="a") String a, 
            @Path("somePath[1]") @Element(name="a") String b) {
         this.a = a;
         this.b = b;
      }
   }
   
   @Root
   public static class OtherCaseExample {
      @Path("FirstPath[1]/SecondPath[1]")
      @Element
      private final String a;
      @Path("FirstPath[1]/secondPath[1]")
      @Element
      private final String b;
      public OtherCaseExample(
            @Path("FirstPath[1]/SecondPath[1]") @Element(name="a") String a, 
            @Path("FirstPath[1]/secondPath[1]") @Element(name="b") String b) {
         this.a = a;
         this.b = b;
      }
   }
   
   public void testNoStyle() throws Exception {
      CaseExample example = new CaseExample("a", "b");
      Persister persister = new Persister();
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      System.out.println(writer);
      CaseExample restored = persister.read(CaseExample.class, writer.toString());
      assertEquals(example.a, restored.a);
      assertEquals(example.b, restored.b);
   } 
   
   public void testStyle() throws Exception {
      Style style = new CamelCaseStyle();
      Format format = new Format(style);
      CollisionExample example = new CollisionExample("a", "b");
      Persister persister = new Persister(format);
      StringWriter writer = new StringWriter();
      boolean exception = false;
      try {
         persister.write(example, writer);
      }catch(Exception e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Exception must be thrown when paths collide on case", exception);
   } 
   
   public void testOtherStyle() throws Exception {
      Style style = new CamelCaseStyle();
      Format format = new Format(style);
      OtherCaseExample example = new OtherCaseExample("a", "b");
      Persister persister = new Persister(format);
      StringWriter writer = new StringWriter();
      boolean exception = false;
      try {
         persister.write(example, writer);
      }catch(Exception e) {
         e.printStackTrace();
         exception = true;
      }
      System.out.println(writer.toString());
      assertFalse("No exception should be thrown with the elements are not the same name", exception);
   } 

}
