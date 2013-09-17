package org.simpleframework.xml.strategy;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.convert.Convert;
import org.simpleframework.xml.convert.Converter;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

public class ConverterMapTest extends ValidationTestCase {
   
   private static class MapConverter implements Converter<java.util.Map> {
      
      public Map read(InputNode node) throws Exception{
         java.util.Map map = new HashMap();
         
         while(true) {
            InputNode next = node.getNext("entry");
            
            if(next == null) {
               break;
            }
            Entry entry = readEntry(next);
      
            map.put(entry.name, entry.value);
         }
         return map;
      }
      
      public void write(OutputNode node, Map map) throws Exception {
         Set keys = map.keySet();
         
         for(Object key : keys) {
            OutputNode next = node.getChild("entry");
            next.setAttribute("key", key.toString());
            OutputNode value = next.getChild("value");
            value.setValue(map.get(key).toString());
         }
      }
      
      private Entry readEntry(InputNode node) throws Exception {
         InputNode key = node.getAttribute("key");
         InputNode value = node.getNext("value");
         
         return new Entry(key.getValue(), value.getValue());
      }
      
      private static class Entry {
         private String name;
         private String value;
         public Entry(String name, String value){
            this.name = name;
            this.value = value;
         }
      }
   }
   
   @Root
   @Default
   private static class MapHolder {
      @Element
      @ElementMap
      @Convert(MapConverter.class)
      private Map<String, String> map = new HashMap<String, String>();
      public void put(String name, String value){
         map.put(name, value);
      }
   }
   
   public void testMap() throws Exception {
      Strategy strategy = new AnnotationStrategy();
      Serializer serializer = new Persister(strategy);
      MapHolder holder = new MapHolder();
      
      holder.put("a", "A");
      holder.put("b", "B");
      holder.put("c", "C");
      holder.put("d", "D");
      holder.put("e", "E");
      holder.put("f", "F");
      
      serializer.write(holder, System.out);
      
      validate(holder, serializer);
   }

}
