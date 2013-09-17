package org.simpleframework.xml.core;

import java.util.ArrayList;
import java.util.Collection;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Contact;
import org.simpleframework.xml.core.FieldScanner;

import junit.framework.TestCase;

public class FieldScannerTest extends TestCase {
   
   @Root(name="name")
   public static class Example {

      @ElementList(name="list", type=Entry.class)
      private Collection<Entry> list;
      
      @Attribute(name="version")
      private int version;
      
      @Attribute(name="name")
      private String name;
   }
   
   @Root(name="entry")
   public static class Entry {
      
      @Attribute(name="text")
      public String text;
   }
   
   public void testExample() throws Exception {
      FieldScanner scanner = new FieldScanner(new DetailScanner(Example.class), new Support());
      ArrayList<Class> list = new ArrayList<Class>();
      
      for(Contact contact : scanner) {
         list.add(contact.getType());
      }
      assertEquals(scanner.size(), 3);
      assertTrue(list.contains(Collection.class));
      assertTrue(list.contains(String.class));
      assertTrue(list.contains(int.class));
   }

}
