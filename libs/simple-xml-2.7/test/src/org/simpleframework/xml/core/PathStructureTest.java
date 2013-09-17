package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.LinkedList;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Order;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.strategy.TreeStrategy;
import org.simpleframework.xml.stream.CamelCaseStyle;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.Style;

public class PathStructureTest extends ValidationTestCase {
   
   @Order(
   elements={
      "some/path",
      "some/other/path/first",
      "some/other/path/second",
      "some/other/path/third",      
      "fifth",
      "sixth",
      "group/example",
      "group/blah/eight",
      "ninth",        
      "some/other/path/tenth"
   },
   attributes={
      "some/path/@a",
      "group/example/@b",
      "group/example/@c",
      "some/path/@d",
      "some/path/@e",
      "f",
      "g",
      "some/path/@h"   
   }
   )
   @Root
   @SuppressWarnings("all")
   private static class ExampleAssembledType {
      @Element 
      @Path("some/other/path")
      private String first;
      @Element
      @Path("some/other/path")
      private String second;
      @Element 
      @Path("some/other/path")
      private String third;      
      @Element 
      @Path("some/other")
      private String fourth;
      @Element 
      private String fifth;
      @Element
      private String sixth;
      @Element
      @Path("group")
      private String seventh;      
      @Element 
      @Path("group/blah")
      private String eight;
      @Element
      private String ninth;
      @Element
      @Path("some/other/path")
      private String tenth;
      @Attribute
      @Path("some/path")
      private String a;
      @Attribute
      @Path("group/example")
      private String b;
      @Attribute
      @Path("group/example")
      private String c;
      @Attribute
      @Path("some/path")
      private String d;
      @Attribute
      @Path("some/path")
      private String e;
      @Attribute      
      private String f;
      @Attribute     
      private String g;
      @Attribute
      @Path("some/path")
      private String h;
      public ExampleAssembledType() {
         super();
      }
      public void setFirst(String first) {
         this.first = first;
      }
      public void setSecond(String second) {
         this.second = second;
      }
      public void setThird(String third) {
         this.third = third;
      }
      public void setFourth(String fourth) {
         this.fourth = fourth;
      }
      public void setFifth(String fifth) {
         this.fifth = fifth;
      }
      public void setSixth(String sixth) {
         this.sixth = sixth;
      }
      public void setSeventh(String seventh) {
         this.seventh = seventh;
      }
      public void setEight(String eight) {
         this.eight = eight;
      }
      public void setNinth(String ninth) {
         this.ninth = ninth;
      }
      public void setTenth(String tenth) {
         this.tenth = tenth;
      }
      public void setA(String a) {
         this.a = a;
      }
      public void setB(String b) {
         this.b = b;
      }
      public void setC(String c) {
         this.c = c;
      }
      public void setD(String d) {
         this.d = d;
      }
      public void setE(String e) {
         this.e = e;
      }
      public void setF(String f) {
         this.f = f;
      }
      public void setG(String g) {
         this.g = g;
      }
      public void setH(String h) {
         this.h = h;
      }      
   }
   
   public void testOrder() throws Exception {
      Scanner scanner = new ObjectScanner(new DetailScanner(ExampleAssembledType.class), new Support());      
      
      validate(scanner.getSection(), "some", "fifth", "sixth", "group", "ninth");
      validate(scanner.getSection().getSection("some"), "path", "other");
      validate(scanner.getSection().getSection("some").getSection("other"), "path", "fourth");
      validate(scanner.getSection().getSection("some").getSection("other").getSection("path"), "first", "second", "third", "tenth");      
   }
   
   public void testSerialization() throws Exception {
      Style style = new CamelCaseStyle();
      Format format = new Format(style);
      Persister camelPersister = new Persister(format);
      Persister persister = new Persister();
      ExampleAssembledType example = new ExampleAssembledType();
      StringWriter writer = new StringWriter();
      StringWriter camelWriter = new StringWriter();
      example.setA("This is a");
      example.setB("This is b");
      example.setC("This is c");
      example.setD("This is d");
      example.setE("This is e");
      example.setF("This is f");
      example.setG("This is g");
      example.setH("This is h");
      example.setFirst("The first element");
      example.setSecond("The second element");
      example.setThird("The third element");
      example.setFourth("The fourth element");
      example.setFifth("The fifth element");
      example.setSixth("The sixth element");
      example.setSeventh("The seventh element");
      example.setEight("The eight element");
      example.setNinth("The ninth element");
      example.setTenth("The tenth element");
      camelPersister.write(example, System.err);
      persister.write(example, System.out);
      persister.write(example, writer);
      camelPersister.write(example, camelWriter);
      
      ExampleAssembledType recovered = persister.read(ExampleAssembledType.class, writer.toString());
      ExampleAssembledType camelRecovered = camelPersister.read(ExampleAssembledType.class, camelWriter.toString());
      
      assertEquals(recovered.a, example.a);
      assertEquals(recovered.b, example.b);
      assertEquals(recovered.c, example.c);
      assertEquals(recovered.d, example.d);
      assertEquals(recovered.e, example.e);
      assertEquals(recovered.f, example.f);
      assertEquals(recovered.g, example.g);
      assertEquals(recovered.h, example.h);
      assertEquals(recovered.first, example.first);
      assertEquals(recovered.second, example.second);
      assertEquals(recovered.third, example.third);
      assertEquals(recovered.fourth, example.fourth);
      assertEquals(recovered.fifth, example.fifth);
      assertEquals(recovered.sixth, example.sixth);
      assertEquals(recovered.seventh, example.seventh);
      assertEquals(recovered.eight, example.eight);
      assertEquals(recovered.ninth, example.ninth);
      assertEquals(recovered.tenth, example.tenth);
      
      assertEquals(camelRecovered.a, example.a);
      assertEquals(camelRecovered.b, example.b);
      assertEquals(camelRecovered.c, example.c);
      assertEquals(camelRecovered.d, example.d);
      assertEquals(camelRecovered.e, example.e);
      assertEquals(camelRecovered.f, example.f);
      assertEquals(camelRecovered.g, example.g);
      assertEquals(camelRecovered.h, example.h);
      assertEquals(camelRecovered.first, example.first);
      assertEquals(camelRecovered.second, example.second);
      assertEquals(camelRecovered.third, example.third);
      assertEquals(camelRecovered.fourth, example.fourth);
      assertEquals(camelRecovered.fifth, example.fifth);
      assertEquals(camelRecovered.sixth, example.sixth);
      assertEquals(camelRecovered.seventh, example.seventh);
      assertEquals(camelRecovered.eight, example.eight);
      assertEquals(camelRecovered.ninth, example.ninth);
      assertEquals(camelRecovered.tenth, example.tenth);
     
      validate(example, persister);
   }
   
   private void validate(Section section, String... order) throws Exception {
      LinkedList<String> expect = asList(order);
      for(String name : section) {
         System.out.println(name);
      }
      for(String name : section) {
         assertEquals(name, expect.removeFirst());
      }       
      assertTrue(expect.isEmpty());
   }
   
   private LinkedList<String> asList(String... values) {
      LinkedList<String> list = new LinkedList<String>();
      for(String value : values) {
         list.add(value);
      }
      return list;
   }
}
