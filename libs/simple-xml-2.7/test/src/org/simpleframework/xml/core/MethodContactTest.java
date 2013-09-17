package org.simpleframework.xml.core;

import java.util.ArrayList;
import java.util.Collection;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Contact;
import org.simpleframework.xml.core.ContactList;
import org.simpleframework.xml.core.MethodScanner;

import junit.framework.TestCase;

public class MethodContactTest extends TestCase {
   
   @Root(name="name")
   public static class Example {

      private Collection<Entry> list;
      
      private float version;
      
      private String name;
      
      @ElementList(name="list", type=Entry.class)
      public void setList(Collection<Entry> list) {
         this.list = list;
      }
      
      @ElementList(name="list", type=Entry.class)
      public Collection<Entry> getList() {
         return list;
      }
      
      @Element(name="version")
      public void setVersion(float version) {
         this.version = version;  
      }
      
      @Element(name="version")
      public float getVersion() {
         return version;
      }
      
      @Attribute(name="name")
      public void setName(String name) {
         this.name = name;
      }
      
      @Attribute(name="name")
      public String getName() {
         return name;
      }
   }
   
   @Root(name="entry")
   public static class Entry {
      
      @Attribute(name="text")
      public String text;
   }
   
   public void testContact() throws Exception {
      MethodScanner scanner = new MethodScanner(new DetailScanner(Example.class), new Support());
      ArrayList<Class> types = new ArrayList<Class>();
     
      for(Contact contact : scanner) {
         types.add(contact.getType());
      }
      assertEquals(scanner.size(), 3);
      assertTrue(types.contains(String.class));
      assertTrue(types.contains(float.class));
      assertTrue(types.contains(Collection.class));
      
      ContactList contacts = scanner;
      Contact version = getContact(float.class, contacts);
      Example example = new Example();      
      
      version.set(example, 1.2f);     
      
      assertEquals(example.version, 1.2f);
      assertNull(example.name);
      assertNull(example.list);
      
      Contact name = getContact(String.class, contacts);      
      
      name.set(example, "name");
      
      assertEquals(example.version, 1.2f);
      assertEquals(example.name, "name");
      assertNull(example.list);
      
      Contact list = getContact(Collection.class, contacts);
      
      list.set(example, types);
      
      assertEquals(example.version, 1.2f);
      assertEquals(example.name, "name");
      assertEquals(example.list, types);      
   }
   
   public Contact getContact(Class type, ContactList from) {      
      for(Contact contact : from) {
         if(type == contact.getType()) {
            return contact;
         }
      }
      return null;
   }

}
