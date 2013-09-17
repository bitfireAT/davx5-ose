package org.simpleframework.xml.core;

import java.util.List;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.ElementListUnion;

public class UnionTest extends TestCase {
   
   private static final String SOURCE = 
   "<unionExample>" +
   "  <integer>" +
   "    <number>111</number>" +
   "  </integer>" +
   "</unionExample>";
   
   private static final String SOURCE_LIST = 
   "<unionExample>" +
   "  <integer>" +
   "    <number>111</number>" +
   "  </integer>" +
   "  <i>" +
   "    <number>111</number>" +
   "  </i>" +
   "  <d>" +
   "    <number>222d</number>" +
   "  </d>" +
   "  <s>" +
   "    <string>SSS</string>" +
   "  </s>" +
   "</unionExample>";
   
   private static final String DOUBLE_LIST =
   "<unionExample>" +
   "  <double>" +
   "    <number>222.2</number>" +
   "  </double>" +
   "  <d>" +
   "    <number>1.1</number>" +
   "  </d>" +
   "  <d>" +
   "    <number>2.2</number>" +
   "  </d>" +
   "</unionExample>";     
   
   @Root(name="string")
   public static class StringEntry implements Entry<String> {
      @Element
      private String string;
      
      public String foo(){
         return string;
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
   public static class UnionExample {
      
      @ElementUnion({
         @Element(name="double", type=DoubleEntry.class),
         @Element(name="string", type=StringEntry.class),
         @Element(name="integer", type=IntegerEntry.class)
      })
      private Entry entry;
   }
   @Root
   public static class UnionListExample {
      
      @ElementUnion({
         @Element(name="double", type=DoubleEntry.class),
         @Element(name="string", type=StringEntry.class),
         @Element(name="integer", type=IntegerEntry.class)
      })
      private Entry entry;
      
      @ElementListUnion({
         @ElementList(entry="d", inline=true, type=DoubleEntry.class),
         @ElementList(entry="s", inline=true, type=StringEntry.class),
         @ElementList(entry="i", inline=true, type=IntegerEntry.class)
      })
      private List<Entry> list;

   }
   public void testListDeserialization() throws Exception {
      Persister persister = new Persister();
      UnionListExample example = persister.read(UnionListExample.class, SOURCE_LIST);
      List<Entry> entry = example.list;
      assertEquals(entry.size(), 3);
      assertEquals(entry.get(0).getClass(), IntegerEntry.class);
      assertEquals(entry.get(0).foo(), 111);
      assertEquals(entry.get(1).getClass(), DoubleEntry.class);
      assertEquals(entry.get(1).foo(), 222.0);
      assertEquals(entry.get(2).getClass(), StringEntry.class);
      assertEquals(entry.get(2).foo(), "SSS");
      assertEquals(example.entry.getClass(), IntegerEntry.class);
      assertEquals(example.entry.foo(), 111);
      persister.write(example, System.out);
   }
   public void testDoubleListDeserialization() throws Exception {
      Persister persister = new Persister();
      UnionListExample example = persister.read(UnionListExample.class, DOUBLE_LIST);
      List<Entry> entry = example.list;
      assertEquals(entry.size(), 2);
      assertEquals(entry.get(0).getClass(), DoubleEntry.class);
      assertEquals(entry.get(0).foo(), 1.1);
      assertEquals(entry.get(1).getClass(), DoubleEntry.class);
      assertEquals(entry.get(1).foo(), 2.2);
      assertEquals(example.entry.getClass(), DoubleEntry.class);
      assertEquals(example.entry.foo(), 222.2);
      persister.write(example, System.out);
   }
}
