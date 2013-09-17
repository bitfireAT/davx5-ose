package org.simpleframework.xml.core;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.ValidationTestCase;

public class NullArrayEntryTest extends ValidationTestCase {
        
   private static final String LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<exampleArray>\n"+
   "   <list length='3'>\n"+   
   "      <substitute id='1'>\n"+
   "         <text>one</text>  \n\r"+
   "      </substitute>\n\r"+
   "      <substitute/>\n"+
   "      <substitute/>\n"+
   "   </list>\n"+
   "</exampleArray>";
   
   private static final String PRIMITIVE_LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<examplePrimitiveArray>\n"+
   "  <list length='4'>\r\n" +
   "     <substitute>a</substitute>\n"+
   "     <substitute>b</substitute>\n"+
   "     <substitute/>\r\n"+
   "     <substitute/>\r\n" +
   "  </list>\r\n" +
   "</examplePrimitiveArray>"; 
   
   @Root
   private static class Entry {

      @Attribute           
      private int id;           
           
      @Element
      private String text;   
      
      public Entry() {
         super();
      }
      
      public Entry(String text, int id) {
         this.id = id;
         this.text = text;
      }

      public String getText() {
         return text;
      }
      
      public int getId() {
         return id;
      }
   }
   
   @Root
   private static class ExampleArray {
      
      @ElementArray(name="list", entry="substitute") // XXX bad error if the length= attribute is missing
      private Entry[] list;
      
      public Entry[] getArray() {
         return list;
      }
   }
   
   @Root
   private static class ExamplePrimitiveArray {
      
      @ElementArray(name="list", entry="substitute") // XXX bad error if this was an array
      private String[] list;
      
      public String[] getArray() {
         return list;
      }
   }

   
   public void testExampleArray() throws Exception {
      Serializer serializer = new Persister();
      ExampleArray list = serializer.read(ExampleArray.class, LIST);
      
      list.list = new Entry[] { new Entry("a", 1), new Entry("b", 2), null, null, new Entry("e", 5) };
      
      serializer.write(list, System.out);
      validate(list, serializer);      
   }  
   
   public void testExamplePrimitiveArray() throws Exception {
      Serializer serializer = new Persister();
      ExamplePrimitiveArray list = serializer.read(ExamplePrimitiveArray.class, PRIMITIVE_LIST);
      
      list.list = new String[] { "a", "b", null, null, "e", null, "f" };
      
      serializer.write(list, System.out);
      validate(list, serializer);      
   }
}
