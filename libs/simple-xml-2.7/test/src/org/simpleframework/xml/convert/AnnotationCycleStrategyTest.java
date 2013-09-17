package org.simpleframework.xml.convert;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.convert.ExampleConverters.Entry;
import org.simpleframework.xml.convert.ExampleConverters.OtherEntryConverter;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.CycleStrategy;

public class AnnotationCycleStrategyTest extends ValidationTestCase {

   @Root
   public static class EntryListExample {
      @ElementList(inline=true)
      private List<Entry> list = new ArrayList<Entry>();
      @Element
      @Convert(OtherEntryConverter.class)
      private Entry primary;
      public Entry getPrimary() {
         return primary;
      }
      public void setPrimary(Entry primary) {
         this.primary = primary;
      }
      public void addEntry(Entry entry){
         list.add(entry);
      }
      public List<Entry> getEntries(){
         return list;
      }
   }
   
   public void testCycle() throws Exception {
      CycleStrategy inner = new CycleStrategy();
      AnnotationStrategy strategy = new AnnotationStrategy(inner);
      Persister persister = new Persister(strategy);
      EntryListExample list = new EntryListExample();
      StringWriter writer = new StringWriter();
   
      Entry a = new Entry("A", "a");
      Entry b = new Entry("B", "b");
      Entry c = new Entry("C", "c");
      Entry primary = new Entry("PRIMARY", "primary");
      
      list.setPrimary(primary);
      list.addEntry(a);
      list.addEntry(b);
      list.addEntry(c);
      list.addEntry(b);
      list.addEntry(c);
      
      persister.write(list, writer);
      persister.write(list, System.out);
   
      String text = writer.toString();
      EntryListExample copy = persister.read(EntryListExample.class, text);
      
      assertEquals(copy.getEntries().get(0), list.getEntries().get(0));
      assertEquals(copy.getEntries().get(1), list.getEntries().get(1));
      assertEquals(copy.getEntries().get(2), list.getEntries().get(2));
      assertEquals(copy.getEntries().get(3), list.getEntries().get(3));
      assertEquals(copy.getEntries().get(4), list.getEntries().get(4));
      
      assertTrue(copy.getEntries().get(2) == copy.getEntries().get(4)); // cycle
      assertTrue(copy.getEntries().get(1) == copy.getEntries().get(3)); // cycle
      
      assertElementExists(text, "/entryListExample");
      assertElementExists(text, "/entryListExample/entry[1]");
      assertElementExists(text, "/entryListExample/entry[1]/name");
      assertElementExists(text, "/entryListExample/entry[1]/value");
      assertElementHasValue(text, "/entryListExample/entry[1]/name", "A");
      assertElementHasValue(text, "/entryListExample/entry[1]/value", "a");
      assertElementExists(text, "/entryListExample/entry[2]/name");
      assertElementExists(text, "/entryListExample/entry[2]/value");
      assertElementHasValue(text, "/entryListExample/entry[2]/name", "B");
      assertElementHasValue(text, "/entryListExample/entry[2]/value", "b");
      assertElementExists(text, "/entryListExample/entry[3]/name");
      assertElementExists(text, "/entryListExample/entry[3]/value");
      assertElementHasValue(text, "/entryListExample/entry[3]/name", "C");
      assertElementHasValue(text, "/entryListExample/entry[3]/value", "c");
      assertElementExists(text, "/entryListExample/entry[4]");
      assertElementExists(text, "/entryListExample/entry[5]");
      assertElementHasAttribute(text, "/entryListExample/entry[4]", "reference", "2"); // cycle
      assertElementHasAttribute(text, "/entryListExample/entry[5]", "reference", "3"); // cycle
      assertElementExists(text, "/entryListExample/primary");
      assertElementHasAttribute(text, "/entryListExample/primary", "name", "PRIMARY"); // other converter
      assertElementHasAttribute(text, "/entryListExample/primary", "value", "primary"); // other converter
   }
   
}
