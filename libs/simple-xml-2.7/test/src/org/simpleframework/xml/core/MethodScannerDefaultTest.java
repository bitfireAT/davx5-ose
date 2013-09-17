package org.simpleframework.xml.core;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.DefaultType;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;

public class MethodScannerDefaultTest extends TestCase {

   @Default(DefaultType.PROPERTY)
   public static class NoAnnotations {
      private String[] array;
      private Map<String, String> map;
      private List<String> list;
      private Date date;
      private String customer;
      private String name;
      private int price;
      public Date getDate() {
         return date;
      }
      public void setArray(String[] array) {
         this.array = array;
      }
      public String[] getArray() {
         return array;
      }
      public void setMap(Map<String, String> map) {
         this.map = map;
      }
      public Map<String, String> getMap() {
         return map;
      }
      public void setList(List<String> list) {
         this.list = list;
      }
      public List<String> getList() {
         return list;
      }
      public void setDate(Date date) {
         this.date = date;
      }
      public String getCustomer() {
         return customer;
      }
      public void setCustomer(String customer) {
         this.customer = customer;
      }
      public String getName() {
         return name;
      }
      public void setName(String name) {
         this.name = name;
      }
      public int getPrice() {
         return price;
      }
      public void setPrice(int price) {
         this.price = price;
      }
   }
   
   @Default(DefaultType.PROPERTY)
   public static class MixedAnnotations {
      private String[] array;
      private Map<String, String> map;
      private String name;
      private int value;
      @Attribute
      public String getName() {
         return name;
      }
      @Attribute
      public void setName(String name) {
         this.name = name;
      }
      @Element(data=true)
      public int getValue() {
         return value;
      }
      @Element(data=true)
      public void setValue(int value) {
         this.value = value;
      }
      public void setArray(String[] array) {
         this.array = array;
      }
      public String[] getArray() {
         return array;
      }
      public void setMap(Map<String, String> map) {
         this.map = map;
      }
      public Map<String, String> getMap() {
         return map;
      }   
   }
   
   public static class ExtendedAnnotations extends MixedAnnotations {
      @Element
      public String[] getArray() {
         return super.getArray();
      }
      @Element
      public void setArray(String[] array) {
         super.setArray(array);
      }
   }
   
   public void testNoAnnotationsWithNoDefaults() throws Exception {
      Map<String, Contact> map = getContacts(NoAnnotations.class);
      
      assertFalse(map.isEmpty());
   }
   
   public void testMixedAnnotationsWithNoDefaults() throws Exception {
      Map<String, Contact> map = getContacts(MixedAnnotations.class);
      
      assertEquals(map.size(), 4);
      assertFalse(map.get("name").isReadOnly());
      assertFalse(map.get("value").isReadOnly());
      
      assertEquals(int.class, map.get("value").getType());      
      assertEquals(String.class, map.get("name").getType());
      
      assertEquals(Attribute.class, map.get("name").getAnnotation().annotationType());
      assertEquals(Element.class, map.get("value").getAnnotation().annotationType());
      
      assertEquals(Attribute.class, map.get("name").getAnnotation(Attribute.class).annotationType());
      assertEquals(Element.class, map.get("value").getAnnotation(Element.class).annotationType());
      
      assertNull(map.get("name").getAnnotation(Root.class));
      assertNull(map.get("value").getAnnotation(Root.class));
   }
   
   public void testExtendedAnnotations() throws Exception {
      Map<String, Contact> map = getContacts(ExtendedAnnotations.class);
      
      assertFalse(map.get("array").isReadOnly());
      assertFalse(map.get("map").isReadOnly());
      assertFalse(map.get("name").isReadOnly());      
      assertFalse(map.get("value").isReadOnly());
      
      assertEquals(String[].class, map.get("array").getType());
      assertEquals(Map.class, map.get("map").getType());
      assertEquals(int.class, map.get("value").getType());      
      assertEquals(String.class, map.get("name").getType());
      
      assertEquals(Attribute.class, map.get("name").getAnnotation().annotationType());
      assertEquals(Element.class, map.get("value").getAnnotation().annotationType());
      assertEquals(ElementMap.class, map.get("map").getAnnotation().annotationType());
      assertEquals(Element.class, map.get("array").getAnnotation().annotationType());
      
      assertEquals(Attribute.class, map.get("name").getAnnotation(Attribute.class).annotationType());
      assertEquals(Element.class, map.get("value").getAnnotation(Element.class).annotationType());
      assertEquals(ElementMap.class, map.get("map").getAnnotation(ElementMap.class).annotationType());
      assertEquals(Element.class, map.get("array").getAnnotation(Element.class).annotationType());
      
      assertNull(map.get("name").getAnnotation(Root.class));
      assertNull(map.get("value").getAnnotation(Root.class));
      assertNull(map.get("map").getAnnotation(Root.class));
      assertNull(map.get("array").getAnnotation(Root.class));
   }
   
   public void testMixedAnnotations() throws Exception {
      Map<String, Contact> map = getContacts(MixedAnnotations.class);
      
      assertFalse(map.get("array").isReadOnly());
      assertFalse(map.get("map").isReadOnly());
      assertFalse(map.get("name").isReadOnly());      
      assertFalse(map.get("value").isReadOnly());
      
      assertEquals(String[].class, map.get("array").getType());
      assertEquals(Map.class, map.get("map").getType());
      assertEquals(int.class, map.get("value").getType());      
      assertEquals(String.class, map.get("name").getType());
      
      assertEquals(Attribute.class, map.get("name").getAnnotation().annotationType());
      assertEquals(Element.class, map.get("value").getAnnotation().annotationType());
      assertEquals(ElementMap.class, map.get("map").getAnnotation().annotationType());
      assertEquals(ElementArray.class, map.get("array").getAnnotation().annotationType());
      
      assertEquals(Attribute.class, map.get("name").getAnnotation(Attribute.class).annotationType());
      assertEquals(Element.class, map.get("value").getAnnotation(Element.class).annotationType());
      assertEquals(ElementMap.class, map.get("map").getAnnotation(ElementMap.class).annotationType());
      assertEquals(ElementArray.class, map.get("array").getAnnotation(ElementArray.class).annotationType());
      
      assertNull(map.get("name").getAnnotation(Root.class));
      assertNull(map.get("value").getAnnotation(Root.class));
      assertNull(map.get("map").getAnnotation(Root.class));
      assertNull(map.get("array").getAnnotation(Root.class));
   }
   
   public void testNoAnnotations() throws Exception {
      Map<String, Contact> map = getContacts(NoAnnotations.class);
      
      assertFalse(map.get("date").isReadOnly());
      assertFalse(map.get("customer").isReadOnly());
      assertFalse(map.get("name").isReadOnly());      
      assertFalse(map.get("price").isReadOnly());
      assertFalse(map.get("list").isReadOnly());
      assertFalse(map.get("map").isReadOnly());
      assertFalse(map.get("array").isReadOnly());
      
      assertEquals(Date.class, map.get("date").getType());
      assertEquals(String.class, map.get("customer").getType());
      assertEquals(String.class, map.get("name").getType());      
      assertEquals(int.class, map.get("price").getType());
      assertEquals(List.class, map.get("list").getType());
      assertEquals(Map.class, map.get("map").getType());
      assertEquals(String[].class, map.get("array").getType());
      
      assertEquals(Element.class, map.get("date").getAnnotation().annotationType());
      assertEquals(Element.class, map.get("customer").getAnnotation().annotationType());
      assertEquals(Element.class, map.get("name").getAnnotation().annotationType());      
      assertEquals(Element.class, map.get("price").getAnnotation().annotationType());
      assertEquals(ElementList.class, map.get("list").getAnnotation().annotationType());
      assertEquals(ElementMap.class, map.get("map").getAnnotation().annotationType());
      assertEquals(ElementArray.class, map.get("array").getAnnotation().annotationType());
      
      assertEquals(Element.class, map.get("date").getAnnotation(Element.class).annotationType());
      assertEquals(Element.class, map.get("customer").getAnnotation(Element.class).annotationType());
      assertEquals(Element.class, map.get("name").getAnnotation(Element.class).annotationType());      
      assertEquals(Element.class, map.get("price").getAnnotation(Element.class).annotationType());
      assertEquals(ElementList.class, map.get("list").getAnnotation(ElementList.class).annotationType());
      assertEquals(ElementMap.class, map.get("map").getAnnotation(ElementMap.class).annotationType());
      assertEquals(ElementArray.class, map.get("array").getAnnotation(ElementArray.class).annotationType());
      
      assertNull(map.get("date").getAnnotation(Root.class));
      assertNull(map.get("customer").getAnnotation(Root.class));
      assertNull(map.get("name").getAnnotation(Root.class));
      assertNull(map.get("price").getAnnotation(Root.class));
      assertNull(map.get("list").getAnnotation(Root.class));
      assertNull(map.get("map").getAnnotation(Root.class));
      assertNull(map.get("array").getAnnotation(Root.class));
   }
   
   private static Map<String, Contact> getContacts(Class type) throws Exception {
      MethodScanner scanner = new MethodScanner(new DetailScanner(type), new Support());
      Map<String, Contact> map = new HashMap<String, Contact>();
      
      for(Contact contact : scanner) {
         map.put(contact.getName(), contact);
      }
      return map;
   }
   
}
