package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class InlineListWithDataTest extends ValidationTestCase {

   @Root
   private static class ListWithDataExample {
      private @ElementList(inline=true, data=true) List<String> list;
      public ListWithDataExample(){
         this.list = new ArrayList<String>();
      }
      public void addValue(String value) {
         list.add(value);
      }
      public List<String> getList() {
         return list;
      }
   }
   
   @Root
   private static class MapWithDataExample {
      private @ElementMap(inline=true, data=true, attribute=true) Map<String, String> map;
      public MapWithDataExample(){
         this.map = new LinkedHashMap<String, String>();
      }
      public void putValue(String name, String value) {
         map.put(name, value);
      }
      public  Map<String, String> getList() {
         return map;
      }
   }
   
   public void testListWithData() throws Exception {
      Persister persister = new Persister();
      ListWithDataExample example = new ListWithDataExample();
      StringWriter writer = new StringWriter();
      
      example.addValue("A");
      example.addValue("B");
      example.addValue("C");
      
      persister.write(example, writer);
      
      String text = writer.toString();
      System.out.println(text);
      
      assertElementHasCDATA(text, "/listWithDataExample/string[1]", "A");
      assertElementHasCDATA(text, "/listWithDataExample/string[2]", "B");
      assertElementHasCDATA(text, "/listWithDataExample/string[3]", "C");
      
      validate(example, persister);
   }
   
   public void testMapWithData() throws Exception {
      Persister persister = new Persister();
      MapWithDataExample example = new MapWithDataExample();
      StringWriter writer = new StringWriter();
      
      example.putValue("A", "1");
      example.putValue("B", "2");
      example.putValue("C", "3");
      
      persister.write(example, writer);
      
      String text = writer.toString();
      System.out.println(text);
      
      assertElementHasCDATA(text, "/mapWithDataExample/entry[1]", "1");
      assertElementHasCDATA(text, "/mapWithDataExample/entry[2]", "2");
      assertElementHasCDATA(text, "/mapWithDataExample/entry[3]", "3");
      
      validate(example, persister);
   }
}
