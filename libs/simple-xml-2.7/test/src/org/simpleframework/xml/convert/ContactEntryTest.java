package org.simpleframework.xml.convert;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.convert.ExampleConverters.Entry;
import org.simpleframework.xml.convert.ExampleConverters.EntryListConverter;
import org.simpleframework.xml.convert.ExampleConverters.ExtendedEntry;
import org.simpleframework.xml.convert.ExampleConverters.OtherEntryConverter;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;

public class ContactEntryTest extends ValidationTestCase {
   
   @Root
   public static class EntryList {
      
      @ElementList(inline=true)
      private List<Entry> list;
      
      @Element
      @Convert(OtherEntryConverter.class)
      private Entry other;  
      
      @Element
      private Entry inheritConverter;
      
      @Element
      private Entry polymorhic;
      
      @Element
      @Convert(EntryListConverter.class)
      private List<Entry> otherList;
      
      public EntryList() {
         this("Default", "Value");
      }
      public EntryList(String name, String value){
         this.list = new ArrayList<Entry>();
         this.otherList = new ArrayList<Entry>();
         this.other = new Entry(name, value);
         this.inheritConverter = new Entry("INHERIT", "inherit");
         this.polymorhic = new ExtendedEntry("POLY", "poly", 12);
      }
      public Entry getInherit() {
         return inheritConverter;
      }
      public Entry getOther() {
         return other;
      }
      public List<Entry> getList() {
         return list;
      }
      public List<Entry> getOtherList() {
         return otherList;
      }
   }
  
   public void testContact() throws Exception {
      Strategy strategy = new AnnotationStrategy();
      Serializer serializer = new Persister(strategy);
      EntryList list = new EntryList("Other", "Value");
      StringWriter writer = new StringWriter();
      
      list.getList().add(new Entry("a", "A"));
      list.getList().add(new Entry("b", "B"));
      list.getList().add(new Entry("c", "C"));
      
      list.getOtherList().add(new Entry("1", "ONE"));
      list.getOtherList().add(new Entry("2", "TWO"));
      list.getOtherList().add(new Entry("3", "THREE"));
      
      serializer.write(list, writer);
      
      String text = writer.toString();
      EntryList copy = serializer.read(EntryList.class, text);
      
      assertEquals(copy.getList().get(0).getName(), list.getList().get(0).getName());
      assertEquals(copy.getList().get(0).getValue(), list.getList().get(0).getValue());
      assertEquals(copy.getList().get(1).getName(), list.getList().get(1).getName());
      assertEquals(copy.getList().get(1).getValue(), list.getList().get(1).getValue());
      assertEquals(copy.getList().get(2).getName(), list.getList().get(2).getName());
      assertEquals(copy.getList().get(2).getValue(), list.getList().get(2).getValue());
      
      assertEquals(copy.getOtherList().get(0).getName(), list.getOtherList().get(0).getName());
      assertEquals(copy.getOtherList().get(0).getValue(), list.getOtherList().get(0).getValue());
      assertEquals(copy.getOtherList().get(1).getName(), list.getOtherList().get(1).getName());
      assertEquals(copy.getOtherList().get(1).getValue(), list.getOtherList().get(1).getValue());
      assertEquals(copy.getOtherList().get(2).getName(), list.getOtherList().get(2).getName());
      assertEquals(copy.getOtherList().get(2).getValue(), list.getOtherList().get(2).getValue());
      
      System.out.println(text);
   }

}
