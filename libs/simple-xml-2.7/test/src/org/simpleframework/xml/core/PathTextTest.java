package org.simpleframework.xml.core;

import java.io.StringWriter;

import junit.framework.TestCase;

import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.stream.CamelCaseStyle;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.Style;

public class PathTextTest extends TestCase {

   @Root
   public static class TextExample {
      @Path("path[1]/details")
      @Text
      private final String a;
      @Path("path[2]/details")
      @Text
      private final String b;
      public TextExample(
            @Path("path[1]/details") @Text String x, 
            @Path("path[2]/details") @Text String z) {
         this.a = x;
         this.b = z;
      }
   }
   
   public void testStyleDup() throws Exception {
      Style style = new CamelCaseStyle();
      Format format = new Format(style);
      TextExample example = new TextExample("a", "b");
      Persister persister = new Persister(format);
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      System.out.println(writer);
      TextExample restored = persister.read(TextExample.class, writer.toString());
      assertEquals(example.a, restored.a);
      assertEquals(example.b, restored.b);
   } 

}
