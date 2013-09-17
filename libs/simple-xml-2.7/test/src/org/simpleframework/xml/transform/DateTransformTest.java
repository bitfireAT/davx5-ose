package org.simpleframework.xml.transform;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.CycleStrategy;
import org.simpleframework.xml.transform.DateTransform;

public class DateTransformTest extends ValidationTestCase {
   
   @Root
   public static class DateExample {
      
      @ElementArray  
      private Date[] array;
      
      @Element
      private Date element;
      
      @Attribute
      private Date attribute;
      
      @ElementList
      private List<Date> list;     
      
      public DateExample() {
         super();
      }
      
      public DateExample(Date date) {
         this.attribute = date;
         this.element = date;         
         this.list = new ArrayList<Date>();
         this.list.add(date);
         this.list.add(date);
         this.array = new Date[1];
         this.array[0] = date;
      }   
   }
   
   public void testDate() throws Exception {
      Date date = new Date();
      DateTransform format = new DateTransform(Date.class);
      String value = format.write(date);
      Date copy = format.read(value);
      
      assertEquals(date, copy);      
   }
   
   public void testPersistence() throws Exception {
      long now = System.currentTimeMillis();
      Date date = new Date(now);      
      Persister persister = new Persister();
      DateExample example = new DateExample(date);
      StringWriter out = new StringWriter();
      
      assertEquals(example.attribute, date);
      assertEquals(example.element, date);
      assertEquals(example.array[0], date);
      assertEquals(example.list.get(0), date);
      assertEquals(example.list.get(1), date);
      
      persister.write(example, out);
      String text = out.toString();
      
      example = persister.read(DateExample.class, text);
      
      assertEquals(example.attribute, date);
      assertEquals(example.element, date);
      assertEquals(example.array[0], date);
      assertEquals(example.list.get(0), date);
      assertEquals(example.list.get(1), date);
      
      validate(example, persister);      
   }
   
   public void testCyclicPersistence() throws Exception {
      long now = System.currentTimeMillis();
      Date date = new Date(now);
      CycleStrategy strategy = new CycleStrategy();
      Persister persister = new Persister(strategy);
      DateExample example = new DateExample(date);
      StringWriter out = new StringWriter();
      
      assertEquals(example.attribute, date);
      assertEquals(example.element, date);
      assertEquals(example.array[0], date);
      assertEquals(example.list.get(0), date);
      assertEquals(example.list.get(1), date);
      
      persister.write(example, out);
      String text = out.toString();
      
      assertElementHasAttribute(text, "/dateExample", "id", "0");
      assertElementHasAttribute(text, "/dateExample/array", "id", "1");
      assertElementHasAttribute(text, "/dateExample/array/date", "id", "2");
      assertElementHasAttribute(text, "/dateExample/element", "reference", "2");
      assertElementHasAttribute(text, "/dateExample/list", "id", "3");
      
      example = persister.read(DateExample.class, text);
      
      assertEquals(example.attribute, date);
      assertEquals(example.element, date);
      assertEquals(example.array[0], date);
      assertEquals(example.list.get(0), date);
      assertEquals(example.list.get(1), date);
      
      validate(example, persister);      
   }
}
