package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.List;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

public class ExceptionTest extends TestCase {
        
   private static final String VALID = 
   "<?xml version=\"1.0\"?>\n"+
   "<root name='example'>\n"+
   "   <text>some text element</text>\n"+
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
   
   private static final String NO_NAME_ATTRIBUTE = 
   "<?xml version=\"1.0\"?>\n"+
   "<root>\n"+
   "   <text>some text element</text>\n"+
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

   private static final String NO_TEXT_ELEMENT = 
   "<?xml version=\"1.0\"?>\n"+   
   "<root>\n"+
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
   
   private static final String EXTRA_ELEMENT = 
   "<?xml version=\"1.0\"?>\n"+
   "<root name='example'>\n"+
   "   <error>this is an extra element</error>\n"+
   "   <text>some text element</text>\n"+
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
  
   private static final String EXTRA_ATTRIBUTE = 
   "<?xml version=\"1.0\"?>\n"+
   "<root error='some extra attribute' name='example'>\n"+
   "   <text>some text element</text>\n"+
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

   private static final String MISSING_ROOT = 
   "<?xml version=\"1.0\"?>\n"+
   "<root name='example'>\n"+
   "   <text>some text element</text>\n"+
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
   "   <error-list class='java.util.ArrayList'>\n"+
   "      <entry id='10'>\n"+
   "         <text>some text</text>\n"+
   "      </entry>\n"+
   "   </error-list>\n"+
   "</root>"; 

   private static final String LIST_ENTRY_WITH_NO_ROOT = 
   "<?xml version=\"1.0\"?>\n"+
   "<root>\n"+
   "   <list class='java.util.Vector'>\n"+   
   "      <entry id='12' value='some value' class='org.simpleframework.xml.core.ExceptionTest$RootListEntry'>\n"+
   "         <text>some example text</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='34' class='org.simpleframework.xml.core.ExceptionTest$NoRootListEntry'>\n"+
   "         <value>this is the value</value>\n"+
   "         <text>other example</text>  \n\r"+
   "      </entry>\n"+
   "      <entry id='56' class='org.simpleframework.xml.core.ExceptionTest$NoRootListEntry'>\n"+
   "         <value>this is some other value</value>\n"+
   "         <text>final example</text>  \n\r"+
   "      </entry>\n"+
   "   </list>\n"+
   "</root>"; 
   
   @Root(name="entry")
   private static class Entry {

      @Attribute(name="id", required=false)           
      private int id;           
           
      @Element(name="text", required=true)
      private String text;           
   }


   @Root(name="root")
   private static class EntryList {

      @ElementList(name="list", type=Entry.class, required=false)
      private List list;           

      @Attribute(name="name", required=true)
      private String name;

      @Element(name="text", required=true)
      private String text;
   }

   private static class ListEntry {

      @Attribute(name="id", required=false)
      private int id;

      @Element(name="text", required=true)
      private String text;
   }

   @Root(name="entry")
   private static class RootListEntry extends ListEntry {

      @Attribute(name="value", required=true)
      private String value;      
   }

   private static class NoRootListEntry extends ListEntry {

      @Element(name="value", required=true)  
      private String value;         
   }

   @Root(name="root")
   private static class ListEntryList {

      @ElementList(name="list", type=ListEntry.class, required=true)
      private List list;
   }
        
	private Persister serializer;

	public void setUp() {
	   serializer = new Persister();
	}
	
   public void testValid() {    
      try {           
         serializer.read(EntryList.class, VALID);
      }catch(Exception e) {
         e.printStackTrace();
         assertTrue(false);              
      }         
   }
   
   public void testNoAttribute() throws Exception {
      boolean success = false;
      
      try {           
         serializer.read(EntryList.class, NO_NAME_ATTRIBUTE);
      }catch(ValueRequiredException e) {
         success = true;                               
      }
      assertTrue(success);
   }

   public void testNoElement() throws Exception {
      boolean success = false;
      
      try {           
         serializer.read(EntryList.class, NO_TEXT_ELEMENT);
      }catch(ValueRequiredException e) {
         success = true;              
      }         
      assertTrue(success);
   }

   public void testExtraAttribute() throws Exception {
      boolean success = false;

      try {
         serializer.read(EntryList.class, EXTRA_ATTRIBUTE);
      }catch(AttributeException e) {
         success = true;              
      }      
      assertTrue(success);
   }

   public void testExtraElement() throws Exception {
      boolean success = false;

      try {
         serializer.read(EntryList.class, EXTRA_ELEMENT);
      }catch(ElementException e) {
         success = true;              
      }      
      assertTrue(success);
   }
   
   public void testMissingElement() throws Exception {
      boolean success = false;
      
      try {           
         Entry entry = new Entry(); 
         entry.id = 1;
         
         serializer.write(entry, new StringWriter());
      } catch(ElementException e) {
         success = true;              
      }     
      assertTrue(success);
   }

   public void testMissingAttribute() throws Exception {
      boolean success = false;

      try {
         EntryList list = new EntryList();
         list.text = "some text";

         serializer.write(list, new StringWriter());         
      } catch(AttributeException e) {
         success = true;              
      }            
      assertTrue(success);              
   }
}
