package org.simpleframework.xml.core;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;

import junit.framework.TestCase;

public class OverrideTest extends TestCase {
        
   private static final String LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<root name='example'>\n"+
   "   <list class='java.util.Vector'>\n"+   
   "      <entry id='12'>\n"+
   "         <text>some example text</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='34'>\n"+
   "         <text>other example</text>  \n\r"+
   "      </entry>\n"+
   "      <entry id='56'>\n"+
   "         <text>final example</text>  \n\r"+
   "      </entry>\n"+
   "   </list>\n"+
   "</root>";  

   private static final String ENTRY =
   "<?xml version=\"1.0\"?>\n"+
   "<entry id='12'>\n"+
   "   <text>entry text</text>  \n\r"+
   "</entry>";

   private static final String INTERFACE = 
   "<entry id='12' class='org.simpleframework.xml.core.OverrideTest$Entry'>\n"+
   "   <text>entry text</text>  \n\r"+
   "</entry>";

   private static interface EntryInterface {

      public int getId();

      public String getText();
   }
   
   @Root(name="entry")
   private static class Entry implements EntryInterface {

      @Attribute(name="id")           
      private int id;           
           
      @Element(name="text")
      private String text;           

      public int getId() {
         return id;
      }

      public String getText() {
         return text;
      }
   }

   @Root(name="root")
   private static class EntryList {

      @ElementList(name="list", type=Entry.class)
      private List list;           

      @Attribute(name="name")
      private String name;

      public Entry getEntry(int index) {
         return (Entry) list.get(index);  
      }
   }
        
	private Persister serializer;

	public void setUp() {
	   serializer = new Persister();
	}
	
   public void testComposite() throws Exception {    
      Entry entry = serializer.read(Entry.class, new StringReader(ENTRY));
      
      assertEquals(entry.id, 12);
      assertEquals(entry.text, "entry text");
   }

   public void testInterface() throws Exception {
      EntryInterface entry = serializer.read(EntryInterface.class, new StringReader(INTERFACE));

      assertEquals(entry.getId(), 12);
      assertEquals(entry.getText(), "entry text");
   }
   
   public void testList() throws Exception {
      EntryList list = serializer.read(EntryList.class, new StringReader(LIST));

      assertEquals(list.name, "example");
      assertTrue(list.list instanceof Vector);
      
      Entry entry = list.getEntry(0);

      assertEquals(entry.id, 12);
      assertEquals(entry.text, "some example text");

      entry = list.getEntry(1);
      
      assertEquals(entry.id, 34);
      assertEquals(entry.text, "other example");

      entry = list.getEntry(2);
      
      assertEquals(entry.id, 56);
      assertEquals(entry.text, "final example");
   }

   public void testCopy() throws Exception {
      EntryList list = serializer.read(EntryList.class, new StringReader(LIST));

      assertEquals(list.name, "example");
      assertTrue(list.list instanceof Vector);
      
      Entry entry = new Entry();
      
      entry.id = 1234;
      entry.text = "replacement";
      list.list = new ArrayList();
      list.name = "change";
      list.list.add(entry);
      
      StringWriter writer = new StringWriter();
      serializer.write(list, writer);
      serializer.write(list, System.out);

      assertTrue(writer.toString().indexOf("java.util.ArrayList") > 0);
      assertTrue(writer.toString().indexOf("change") > 0);

      list = serializer.read(EntryList.class, new StringReader(writer.toString()));

      assertEquals(list.name, "change");
      assertTrue(list.list instanceof ArrayList);

      entry = list.getEntry(0);

      assertEquals(entry.id, 1234);
      assertEquals(entry.text, "replacement");
   }
}
