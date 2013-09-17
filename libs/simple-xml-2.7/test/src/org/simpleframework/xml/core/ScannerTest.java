package org.simpleframework.xml.core;

import java.util.ArrayList;
import java.util.Collection;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;

public class ScannerTest extends TestCase {
   
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
   
   @Root(name="name", strict=false)
   public static class MixedExample extends Example {
      
      private Entry entry;
      
      private String text;
      
      @Element(name="entry", required=false)
      public void setEntry(Entry entry) {
         this.entry = entry;
      }
      
      @Element(name="entry", required=false)
      public Entry getEntry() {
         return entry;
      }
      
      @Element(name="text")
      public void setText(String text) {
         this.text = text;
      }
      
      @Element(name="text")
      public String getText() {
         return text;
      }
   }
   
   @Root
   public static class ExampleWithPath {
      
      @Attribute
      @Path("contact-info/phone")
      private String code;
      
      @Element
      @Path("contact-info/phone")
      private String mobile;
      
      @Element
      @Path("contact-info/phone")
      private String home;
   }
   
   public static class DuplicateAttributeExample extends Example {
      
      private String name;
      
      @Attribute(name="name")
      public void setName(String name) {
         this.name = name;
      }
      
      @Attribute(name="name")
      public String getName() {
         return name;
      }
   }
   
   public static class NonMatchingElementExample {
      
      private String name;
      
      @Element(name="name", required=false)
      public void setName(String name) {
         this.name = name;
      }
      
      @Element(name="name")
      public String getName() {
         return name;
      }
   }
   
   public static class IllegalTextExample extends MixedExample {
      
      @Text
      private String text;
   }
   
   public void testExampleWithPath() throws Exception {
      Scanner scanner = new ObjectScanner(new DetailScanner(ExampleWithPath.class), new Support());
      ArrayList<Class> types = new ArrayList<Class>();
      
      assertEquals(scanner.getSection().getElements().size(), 0);
      assertTrue(scanner.getSection().getSection("contact-info") != null);
      assertEquals(scanner.getSection().getSection("contact-info").getElements().size(), 0);
      assertEquals(scanner.getSection().getSection("contact-info").getAttributes().size(), 0);
      assertTrue(scanner.getSection().getSection("contact-info").getSection("phone") != null);
      assertEquals(scanner.getSection().getSection("contact-info").getSection("phone").getElements().size(), 2);
      assertEquals(scanner.getSection().getSection("contact-info").getSection("phone").getAttributes().size(), 1);
      assertNull(scanner.getText());
      assertTrue(scanner.isStrict());
   }
   
   public void testExample() throws Exception {
      Scanner scanner = new ObjectScanner(new DetailScanner(Example.class), new Support());
      ArrayList<Class> types = new ArrayList<Class>();
      
      assertEquals(scanner.getSection().getElements().size(), 1);
      assertEquals(scanner.getSection().getAttributes().size(), 2);
      assertNull(scanner.getText());
      assertTrue(scanner.isStrict());
      
      for(Label label : scanner.getSection().getElements()) {
         assertTrue(label.getName().equals(label.getName()));
         assertTrue(label.getEntry().equals(label.getEntry()));
         
         types.add(label.getType());
      }
      assertTrue(types.contains(Collection.class));      
      
      for(Label label : scanner.getSection().getAttributes()) {
         assertTrue(label.getName() == label.getName());
         assertTrue(label.getEntry() == label.getEntry());
         
         types.add(label.getType());
      }
      assertTrue(types.contains(int.class));
      assertTrue(types.contains(String.class));
   }
   
   public void testMixedExample() throws Exception {
      Scanner scanner = new ObjectScanner(new DetailScanner(MixedExample.class), new Support());
      ArrayList<Class> types = new ArrayList<Class>();
      
      assertEquals(scanner.getSection().getElements().size(), 3);
      assertEquals(scanner.getSection().getAttributes().size(), 2);
      assertNull(scanner.getText());
      assertFalse(scanner.isStrict());
      
      for(Label label : scanner.getSection().getElements()) {
         assertTrue(label.getName().equals(label.getName()));
         assertTrue(label.getEntry() == label.getEntry());
         
         types.add(label.getType());
      }
      assertTrue(types.contains(Collection.class));
      assertTrue(types.contains(Entry.class));
      assertTrue(types.contains(String.class));
      
      for(Label label : scanner.getSection().getAttributes()) {
         assertTrue(label.getName().equals(label.getName()));
         assertTrue(label.getEntry() == label.getEntry());
         
         types.add(label.getType());
      }
      assertTrue(types.contains(int.class));
      assertTrue(types.contains(String.class));
   }
   
   public void testDuplicateAttribute() {
      boolean success = false;
      
      try {
         new ObjectScanner(new DetailScanner(DuplicateAttributeExample.class), new Support());
      } catch(Exception e) {
         success = true;
      }
      assertTrue(success);
   }
   
   public void testNonMatchingElement() {
      boolean success = false;
      
      try {
         new ObjectScanner(new DetailScanner(NonMatchingElementExample.class), new Support());
      } catch(Exception e) {
         success = true;
      }
      assertTrue(success);
   }
   
   public void testIllegalTextExample() {
      boolean success = false;
      
      try {
         new ObjectScanner(new DetailScanner(IllegalTextExample.class), new Support());
      } catch(Exception e) {
         success = true;
      }
      assertTrue(success);
   }
}
