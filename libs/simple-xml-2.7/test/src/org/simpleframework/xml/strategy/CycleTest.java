package org.simpleframework.xml.strategy;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.CycleStrategy;

import org.simpleframework.xml.ValidationTestCase;

public class CycleTest extends ValidationTestCase {
   
   private static final int ITERATIONS = 1000;
	
   @Root(name="example")
   public static class CycleExample { 
      
      @ElementList(name="list", type=Entry.class)
      private List<Entry> list;
      
      @Element(name="cycle")
      private CycleExample cycle;     
      
      public CycleExample() {
         this.list = new ArrayList();
         this.cycle = this;
      }
      
      public void add(Entry entry) {
         list.add(entry);
      }
      
      public Entry get(int index) {
         return list.get(index);
      }
   }
   
   @Root(name="entry")
   public static class Entry {
      
      @Attribute(name="key")
      private String name;
      
      @Element(name="value")
      private String value;
      
      protected Entry() {
         super();
      }
      
      public Entry(String name, String value) {
         this.name = name;
         this.value = value;
      }
   }
   
   private Persister persister;
   
   public void setUp() throws Exception {
      persister = new Persister(new CycleStrategy("id", "ref"));
   }
   
   public void testCycle() throws Exception {
      CycleExample example = new CycleExample();
      Entry one = new Entry("1", "one");
      Entry two = new Entry("2", "two");
      Entry three = new Entry("3", "three");
      Entry threeDuplicate = new Entry("3", "three");
      
      example.add(one);
      example.add(two);
      example.add(three);
      example.add(one);
      example.add(two);
      example.add(threeDuplicate);
      
      assertEquals(example.get(0).value, "one");
      assertEquals(example.get(1).value, "two");
      assertEquals(example.get(2).value, "three");
      assertEquals(example.get(3).value, "one");
      assertEquals(example.get(4).value, "two");
      assertEquals(example.get(5).value, "three");
      assertTrue(example.get(0) == example.get(3));
      assertTrue(example.get(1) == example.get(4));
      assertFalse(example.get(2) == example.get(5));
      
      StringWriter out = new StringWriter();
      persister.write(example, System.out);
      persister.write(example, out);      
      
      example = persister.read(CycleExample.class, out.toString());
      
      assertEquals(example.get(0).value, "one");
      assertEquals(example.get(1).value, "two");
      assertEquals(example.get(2).value, "three");
      assertEquals(example.get(3).value, "one");
      assertEquals(example.get(4).value, "two");
      assertTrue(example.get(0) == example.get(3));
      assertTrue(example.get(1) == example.get(4));
      assertFalse(example.get(2) == example.get(5));
      
      validate(example, persister);
   }
   
   public void testMemory() throws Exception {
      CycleExample example = new CycleExample();
	   Entry one = new Entry("1", "one");
	   Entry two = new Entry("2", "two");
	   Entry three = new Entry("3", "three");
	   Entry threeDuplicate = new Entry("3", "three");
	      
	   example.add(one);
	   example.add(two);
	   example.add(three);
	   example.add(one);
	   example.add(two);
	   example.add(threeDuplicate);      
	   
	   StringWriter out = new StringWriter();
	   persister.write(example, System.out);
	   persister.write(example, out);      
	   
	   for(int i = 0; i < ITERATIONS; i++) {
		  persister.write(example, new StringWriter());
	   }
	   for(int i = 0; i < ITERATIONS; i++) {
	      persister.read(CycleExample.class, out.toString());
	   }	      
	   validate(example, persister);   
   }
}
