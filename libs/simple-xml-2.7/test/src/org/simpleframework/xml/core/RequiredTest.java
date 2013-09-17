package org.simpleframework.xml.core;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Collection;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;

import junit.framework.TestCase;

public class RequiredTest extends TestCase {
        
   private static final String COMPLETE =
   "<?xml version=\"1.0\"?>\n"+
   "<root number='1234' flag='true'>\n"+
   "   <value>complete</value>  \n\r"+
   "</root>";
   

   private static final String OPTIONAL =
   "<?xml version=\"1.0\"?>\n"+
   "<root flag='true'/>";
   
   @Root(name="root")
   private static class Entry {

      @Attribute(name="number", required=false)
      private int number = 9999;     

      @Attribute(name="flag")
      private boolean bool;
      
      @Element(name="value", required=false)
      private String value = "default";

      public int getNumber() {
         return number;              
      }         

      public boolean getFlag() {
         return bool;              
      }

      public String getValue() {
         return value;              
      }
   }
        
   private Persister persister;

   public void setUp() {
      persister = new Persister();
   }
	
   public void testComplete() throws Exception {    
      Entry entry = persister.read(Entry.class, new StringReader(COMPLETE));

      assertEquals("complete", entry.getValue());
      assertEquals(1234, entry.getNumber());
      assertEquals(true, entry.getFlag());      
   }

   public void testOptional() throws Exception {
      Entry entry = persister.read(Entry.class, new StringReader(OPTIONAL));

      assertEquals("default", entry.getValue());
      assertEquals(9999, entry.getNumber());
      assertEquals(true, entry.getFlag());            
   }
}
