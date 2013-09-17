package org.simpleframework.xml.core;

import java.util.List;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.stream.CamelCaseStyle;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.HyphenStyle;
import org.simpleframework.xml.stream.Style;

public class UnionStyleTest extends ValidationTestCase {

   private static final String CAMEL_CASE_SOURCE = 
   "<UnionListExample>" +
   "  <IntegerField>" +
   "    <Number>777</Number>" +
   "  </IntegerField>" +
   "  <IntegerEntry>" +
   "    <Number>111</Number>" +
   "  </IntegerEntry>" +
   "  <DoubleEntry>" +
   "    <Number>222.00</Number>" +
   "  </DoubleEntry>" +
   "  <StringEntry>" +
   "    <Text>A</Text>" +
   "  </StringEntry>" +
   "  <StringEntry>" +
   "    <Text>B</Text>" +
   "  </StringEntry>" +      
   "</UnionListExample>";
 
   private static final String HYPHEN_SOURCE = 
   "<union-list-example>" +
   "  <integer-field>" +
   "    <number>777</number>" +
   "  </integer-field>" +
   "  <integer-entry>" +
   "    <number>111</number>" +
   "  </integer-entry>" +
   "  <double-entry>" +
   "    <number>222.00</number>" +
   "  </double-entry>" +
   "  <string-entry>" +
   "    <text>A</text>" +
   "  </string-entry>" +
   "  <string-entry>" +
   "    <text>B</text>" +
   "  </string-entry>" +      
   "</union-list-example>";
   
   @Root(name="string")
   public static class StringEntry implements Entry<String> {
      @Element
      private String text;
      
      public String foo(){
         return text;
      }
   }
   @Root(name="integer")
   public static class IntegerEntry implements Entry<Integer> {
      @Element
      private Integer number;
      
      public Integer foo() {
         return number;
      }
   }
   @Root(name="double")
   public static class DoubleEntry implements Entry {
      @Element
      private Double number;
      
      public Double foo() {
         return number;
      }
   }
   public static interface Entry<T> {
      public T foo();
   }

   @Root
   public static class UnionListExample {
      
      @ElementUnion({
         @Element(name="doubleField", type=DoubleEntry.class),
         @Element(name="stringField", type=StringEntry.class),
         @Element(name="integerField", type=IntegerEntry.class)
      })
      private Entry entry;
      
      @ElementListUnion({
         @ElementList(entry="doubleEntry", inline=true, type=DoubleEntry.class),
         @ElementList(entry="stringEntry", inline=true, type=StringEntry.class),
         @ElementList(entry="integerEntry", inline=true, type=IntegerEntry.class)
      })
      private List<Entry> list;
   }
   
   public void testCamelCaseStyle() throws Exception {
      Style style = new CamelCaseStyle();
      Format format = new Format(style);
      Persister persister = new Persister(format);
      UnionListExample example = persister.read(UnionListExample.class, CAMEL_CASE_SOURCE);
      List<Entry> entry = example.list;
      assertEquals(entry.size(), 4);
      assertEquals(entry.get(0).getClass(), IntegerEntry.class);
      assertEquals(entry.get(0).foo(), 111);
      assertEquals(entry.get(1).getClass(), DoubleEntry.class);
      assertEquals(entry.get(1).foo(), 222.0);
      assertEquals(entry.get(2).getClass(), StringEntry.class);
      assertEquals(entry.get(2).foo(), "A");
      assertEquals(entry.get(3).getClass(), StringEntry.class);
      assertEquals(entry.get(3).foo(), "B");
      assertEquals(example.entry.getClass(), IntegerEntry.class);
      assertEquals(example.entry.foo(), 777);
      persister.write(example, System.out);
      validate(persister, example);
   }
   
   public void testHyphenStyle() throws Exception {
      Style style = new HyphenStyle();
      Format format = new Format(style);
      Persister persister = new Persister(format);
      UnionListExample example = persister.read(UnionListExample.class, HYPHEN_SOURCE);
      List<Entry> entry = example.list;
      assertEquals(entry.size(), 4);
      assertEquals(entry.get(0).getClass(), IntegerEntry.class);
      assertEquals(entry.get(0).foo(), 111);
      assertEquals(entry.get(1).getClass(), DoubleEntry.class);
      assertEquals(entry.get(1).foo(), 222.0);
      assertEquals(entry.get(2).getClass(), StringEntry.class);
      assertEquals(entry.get(2).foo(), "A");
      assertEquals(entry.get(3).getClass(), StringEntry.class);
      assertEquals(entry.get(3).foo(), "B");
      assertEquals(example.entry.getClass(), IntegerEntry.class);
      assertEquals(example.entry.foo(), 777);
      persister.write(example, System.out);
      validate(persister, example);
   }
}
