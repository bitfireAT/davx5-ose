package org.simpleframework.xml.core;

import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class PathWithScatteredElementsTest extends ValidationTestCase {

   private static final String SOURCE =
   "<test version='ONE'>\n"+
   "   <string>Example 1</string>\r\n"+
   "   <messages>\n"+
   "      <list>\n"+
   "        <message>Some example message</message>\r\n"+
   "      </list>\n"+
   "   </messages>\n"+
   "   <string>Example 2</string>\r\n"+
   "   <string>Example 3</string>\r\n"+
   "</test>";
   
   @Root(name="test")
   private static class PathWithInlinePrimitiveList {
      
      @Element
      @Path("messages/list")
      private String message;

      @ElementList(inline=true)
      private List<String> list;

      @Attribute
      private Version version;              

      public String get(int index) {
         return list.get(index);              
      }
   }

   @Root(name="test")
   private static class PathWithConflictInlinePrimitiveList {
      
      @Element
      @Path("message")
      private String value;

      @ElementList(inline=true, entry="message")
      private List<String> list; 
      
      public PathWithConflictInlinePrimitiveList() {
         this.list = new ArrayList<String>();
      }

      public String get(int index) {
         return list.get(index);              
      }
      
      public void add(String value) {
         list.add(value);              
      }
   }
   
   private static enum Version {     
      ONE,
      TWO,
      THREE
   }
   
   public void testScattering() throws Exception {
      Persister persister = new Persister();
      PathWithInlinePrimitiveList example = persister.read(PathWithInlinePrimitiveList.class, SOURCE);
      
      assertEquals(example.version, Version.ONE);
      assertEquals(example.message, "Some example message");
      assertEquals(example.get(0), "Example 1");
      assertEquals(example.get(1), "Example 2");
      assertEquals(example.get(2), "Example 3");
      
      persister.write(example, System.err);
      validate(example, persister);
   }
   
   public void testConflictScattering() throws Exception {
      Persister persister = new Persister();
      PathWithConflictInlinePrimitiveList example = new PathWithConflictInlinePrimitiveList();
      
      example.value = "Example message";
      example.add("Value 1");
      example.add("Value 2");
      example.add("Value 3");
      example.add("Value 4");
      
      boolean exception = false;
      
      try {
         persister.write(example, System.out);
      }catch(ElementException e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Scattering should conflict", exception);
   }
}
