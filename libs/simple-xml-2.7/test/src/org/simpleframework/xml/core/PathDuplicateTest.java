package org.simpleframework.xml.core;

import java.io.StringWriter;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.stream.CamelCaseStyle;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.Style;

public class PathDuplicateTest extends TestCase {

   @Root
   public static class Example {
      @Path("y[1]")
      @Element(name="x")
      private final String a;
      @Path("y[2]")
      @Element(name="x")
      private final String b;
      public Example(
            @Path("y[1]") @Element(name="x") String x, 
            @Path("y[2]") @Element(name="x") String z) {
         this.a = x;
         this.b = z;
      }
   }
   
   @Root
   public static class StyleExample {
      @Path("path[1]/details")
      @Element(name="name")
      private final String a;
      @Path("path[2]/details")
      @Element(name="name")
      private final String b;
      public StyleExample(
            @Path("path[1]/details") @Element(name="name") String x, 
            @Path("path[2]/details") @Element(name="name") String z) {
         this.a = x;
         this.b = z;
      }
   }
   
   public void testDup() throws Exception {
      Example example = new Example("a", "b");
      Persister persister = new Persister();
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      System.out.println(writer);
      Example restored = persister.read(Example.class, writer.toString());
      assertEquals(example.a, restored.a);
      assertEquals(example.b, restored.b);
   }
   
   public void testStyleDup() throws Exception {
      Style style = new CamelCaseStyle();
      Format format = new Format(style);
      StyleExample example = new StyleExample("a", "b");
      Persister persister = new Persister(format);
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      System.out.println(writer);
      StyleExample restored = persister.read(StyleExample.class, writer.toString());
      assertEquals(example.a, restored.a);
      assertEquals(example.b, restored.b);
   }
}
