package org.simpleframework.xml.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Map;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Contact;
import org.simpleframework.xml.core.Entry;
import org.simpleframework.xml.core.FieldContact;

public class EntryTest extends TestCase {
   
   @Root
   private static class CompositeKey {
      
      @Attribute
      private String value;
      
      public String getValue() {
         return value;
      }
   }
   
   @ElementMap
   private Map<String, String> defaultMap;
   
   @ElementMap(keyType=Integer.class, valueType=Long.class)
   private Map annotatedMap;
   
   @ElementMap(value="value")
   private Map<String, String> bodyMap;
   
   @ElementMap(value="value", key="key", attribute=true)
   private Map<String, String> attributeMap;
   
   @ElementMap(entry="entry")
   private Map<Double, String> entryMap;
   
   @ElementMap
   private Map<CompositeKey, String> compositeMap;
   
   public void testEntry() throws Exception {
      Entry entry = getEntry(EntryTest.class, "defaultMap");
      
      assertEquals(entry.getKeyType().getType(), String.class);
      assertEquals(entry.getValueType().getType(), String.class);
      assertEquals(entry.getValue(), null);
      assertEquals(entry.getKey(), null);
      assertEquals(entry.getEntry(), "entry");      
      
      entry = getEntry(EntryTest.class, "annotatedMap");
      
      assertEquals(entry.getKeyType().getType(), Integer.class);
      assertEquals(entry.getValueType().getType(), Long.class);      
      assertEquals(entry.getValue(), null);
      assertEquals(entry.getKey(), null);
      assertEquals(entry.getEntry(), "entry");
      
      entry = getEntry(EntryTest.class, "bodyMap");
      
      assertEquals(entry.getKeyType().getType(), String.class);
      assertEquals(entry.getValueType().getType(), String.class);
      assertEquals(entry.getValue(), "value");
      assertEquals(entry.getKey(), null);
      assertEquals(entry.getEntry(), "entry");
      
      entry = getEntry(EntryTest.class, "attributeMap");
      
      assertEquals(entry.getKeyType().getType(), String.class);
      assertEquals(entry.getValueType().getType(), String.class);
      assertEquals(entry.getValue(), "value");
      assertEquals(entry.getKey(), "key");
      assertEquals(entry.getEntry(), "entry");
      
      entry = getEntry(EntryTest.class, "entryMap");
      
      assertEquals(entry.getKeyType().getType(), Double.class);
      assertEquals(entry.getValueType().getType(), String.class);      
      assertEquals(entry.getValue(), null);
      assertEquals(entry.getKey(), null);
      assertEquals(entry.getEntry(), "entry");
      
      entry = getEntry(EntryTest.class, "compositeMap");
      
      assertEquals(entry.getKeyType().getType(), CompositeKey.class);
      assertEquals(entry.getValueType().getType(), String.class);      
      assertEquals(entry.getValue(), null);
      assertEquals(entry.getKey(), null);
      assertEquals(entry.getEntry(), "entry");    
   }
   
   public Entry getEntry(Class type, String name) throws Exception {
      Contact contact = getContact(EntryTest.class, name);
      ElementMap label = getField(EntryTest.class, name).getAnnotation(ElementMap.class);
      Entry entry = new Entry(contact, label);
      
      return entry;
   }
   
   public Contact getContact(Class type, String name) throws Exception {
      Field field = getField(type, name);
      Annotation label = field.getAnnotation(ElementMap.class);     
      
      return new FieldContact(field, label,new Annotation[]{label});
   }
   
   public Annotation getAnnotation(Field field) {
      Annotation[] list = field.getDeclaredAnnotations();
      
      for(Annotation label : list) {
         if(label instanceof ElementMap) {
            return label;
         }
      }
      return null;
   }
   
   public Field getField(Class type, String name) throws Exception {
      return type.getDeclaredField(name);
   }

}
