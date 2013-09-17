package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;

public class PathWithTextAndElementTest extends ValidationTestCase {
   
   @Root
   private static class PathWithTextAndElementExample {
      @Path("some/path")
      @Attribute(name="name")
      private String value;
      @Path("some/path")
      @Text
      private String text;
      @Path("some")
      @Element(name="name")
      private String item;
      private PathWithTextAndElementExample(
            @Text String text,
            @Path("some/path") @Attribute(name="name") String value,
            @Path("some") @Element(name="name") String item) 
      {
         this.item = item;
         this.value = value;
         this.text = text;
      }
   }
   
   
   @Root
   private static class OtherPathWithTextAndElementExample {
      @Path("valuePath/path")
      @Attribute(name="name")
      private String a;
      @Path("someOtherPath/path")
      @Element(name="name")
      private String b;
      @Path("valuePath/path")
      @Text
      private String c;
      private OtherPathWithTextAndElementExample(
            @Path("valuePath/path") @Attribute(name="name") String a,
            @Path("someOtherPath/path") @Element(name="name") String b,
            @Text String c) 
      {
         this.a = a;
         this.b = b;
         this.c = c;
      }
   }
   
   @Root
   private static class PathWithMultipleTextExample {
      @Path("valuePath[1]")
      @Attribute
      private String a;
      @Path("valuePath[1]")
      @Text
      private String b;
      @Path("valuePath[2]")
      @Attribute
      private String c;
      @Path("valuePath[2]")
      @Text
      private String d;
      private PathWithMultipleTextExample(
            @Attribute(name="a") String a,
            @Path("valuePath[1]") @Text String b,
            @Attribute(name="c") String c,
            @Path("valuePath[2]") @Text String d)
      {
         this.a = a;
         this.b = b;
         this.c = c;
         this.d = d;
      }
   }
   
   public void testTextWithPath() throws Exception {
      Persister persister = new Persister();
      PathWithTextAndElementExample example = new PathWithTextAndElementExample("T", "A", "E");
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      PathWithTextAndElementExample recovered = persister.read(PathWithTextAndElementExample.class, writer.toString());
      assertEquals(recovered.value, example.value);
      assertEquals(recovered.text, example.text);
      assertEquals(recovered.item, example.item);
      validate(persister, example);
   }

   public void s_testOtherTextWithPath() throws Exception {
      Persister persister = new Persister();
      OtherPathWithTextAndElementExample example = new OtherPathWithTextAndElementExample("T", "A", "E");
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      OtherPathWithTextAndElementExample recovered = persister.read(OtherPathWithTextAndElementExample.class, writer.toString());
      assertEquals(recovered.a, example.a);
      assertEquals(recovered.b, example.b);
      assertEquals(recovered.c, example.c);
      validate(persister, example);
   }
   
   public void s_testTextWithTextPath() throws Exception {
      Persister persister = new Persister();
      PathWithMultipleTextExample example = new PathWithMultipleTextExample("A", "B", "C", "D");
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      PathWithMultipleTextExample recovered = persister.read(PathWithMultipleTextExample.class, writer.toString());
      assertEquals(recovered.a, example.a);
      assertEquals(recovered.b, example.b);
      assertEquals(recovered.c, example.c);
      assertEquals(recovered.d, example.d);
      validate(persister, example);
   }
}
