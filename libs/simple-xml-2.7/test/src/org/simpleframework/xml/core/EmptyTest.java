package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.Collection;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.core.ValueRequiredException;

import org.simpleframework.xml.ValidationTestCase;

public class EmptyTest extends ValidationTestCase {
        
   private static final String EMPTY_ELEMENT =
   "<?xml version=\"1.0\"?>\n"+
   "<test>\n"+
   "  <empty></empty>\r\n"+
   "</test>\n";
   
   private static final String BLANK_ELEMENT =
   "<?xml version=\"1.0\"?>\n"+
   "<test>\n"+
   "  <empty/>\r\n"+
   "</test>";

   private static final String EMPTY_ATTRIBUTE =
   "<?xml version=\"1.0\"?>\n"+
   "<test attribute=''/>\n";
   
   private static final String DEFAULT_ATTRIBUTE =
   "<?xml version=\"1.0\"?>\n"+
   "<test name='John Doe' address='NULL'>\n"+
   "  <description>Some description</description>\r\n"+
   "</test>";

   @Root(name="test")
   private static class RequiredElement {

      @Element(name="empty")
      private String empty;            
   }  

   @Root(name="test")
   private static class OptionalElement {

      @Element(name="empty", required=false)
      private String empty;
   }    
   
   @Root(name="test")
   private static class EmptyCollection {
      
      @ElementList(required=false)
      private Collection<String> empty;
   }
   
   @Root(name="test")
   private static class RequiredMethodElement {
      
      private String text;
      
      @Element
      private void setEmpty(String text) {
         this.text = text;
      }
      
      @Element
      private String getEmpty() {
         return text;
      }
   }

   @Root(name="test")
   private static class RequiredAttribute {

      @Attribute(name="attribute")            
      private String attribute;
   }

   @Root(name="test")
   private static class OptionalAttribute {

      @Attribute(name="attribute", required=false)            
      private String attribute;
   }
   
   @Root(name="test")
   private static class DefaultedAttribute {
      
      @Attribute(empty="NULL")
      private String name;
      
      @Attribute(empty="NULL")
      private String address;
      
      @Element
      private String description;
   }

   private Persister persister;

   public void setUp() throws Exception {
      persister = new Persister();
   }
	
   public void testRequiredEmpty() throws Exception {    
      boolean success = false;
      
      try {           
         persister.read(RequiredElement.class, EMPTY_ELEMENT);      
      } catch(ValueRequiredException e) {
         e.printStackTrace();              
         success = true;              
      }
      assertTrue(success);
   }

   public void testRequiredEmptyMethod() throws Exception {   
      boolean success = false;
      
      try {           
         persister.read(RequiredMethodElement.class, EMPTY_ELEMENT);     
      } catch(ValueRequiredException e) {
         e.printStackTrace();
         success = true;              
      }
      assertTrue(success);   
   }
   
   public void testRequiredBlank() throws Exception {    
      boolean success = false;
      
      try {           
         persister.read(RequiredElement.class, BLANK_ELEMENT);     
      } catch(ValueRequiredException e) {
         e.printStackTrace();
         success = true;              
      }
      assertTrue(success);           
   }   

   public void testOptionalEmpty() throws Exception {   
      boolean success = false;
      
      try {           
         persister.read(RequiredElement.class, EMPTY_ELEMENT);     
      } catch(ValueRequiredException e) {
         e.printStackTrace();
         success = true;              
      }
      assertTrue(success);   
   }

   public void testOptionalBlank() throws Exception {    
      OptionalElement element = persister.read(OptionalElement.class, BLANK_ELEMENT);     

      assertNull(element.empty);
   }
   
   public void testEmptyCollection() throws Exception {    
      EmptyCollection element = persister.read(EmptyCollection.class, BLANK_ELEMENT);     

      assertNotNull(element.empty);   
      assertEquals(element.empty.size(), 0);
      
      validate(element, persister);
      
      element.empty = null;
      
      validate(element, persister);
   } 
   
   public void testRequiredEmptyAttribute() throws Exception {
      RequiredAttribute entry = persister.read(RequiredAttribute.class, EMPTY_ATTRIBUTE);

      assertEquals(entry.attribute, "");      
   }

   public void testOptionalEmptyAttribute() throws Exception {
      OptionalAttribute entry = persister.read(OptionalAttribute.class, EMPTY_ATTRIBUTE);

      assertEquals(entry.attribute, "");      
   }
   
   public void testDefaultedAttribute() throws Exception {
      DefaultedAttribute entry = persister.read(DefaultedAttribute.class, DEFAULT_ATTRIBUTE);

      assertEquals(entry.name, "John Doe");
      assertEquals(entry.address, null);
      assertEquals(entry.description, "Some description");
      
      validate(entry, persister);
      
      entry.name = null;
      
      StringWriter out = new StringWriter();
      persister.write(entry, out);
      String result = out.toString();
      
      assertElementHasAttribute(result, "/test", "name", "NULL");
      assertElementHasAttribute(result, "/test", "address", "NULL");
      assertElementHasValue(result, "/test/description", "Some description");
      
      validate(entry, persister);
   } 
}