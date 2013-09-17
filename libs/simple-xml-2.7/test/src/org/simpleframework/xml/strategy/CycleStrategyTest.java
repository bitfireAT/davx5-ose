package org.simpleframework.xml.strategy;

import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeBuilder;
import org.simpleframework.xml.stream.NodeMap;

public class CycleStrategyTest extends TestCase {

   private static final String ARRAY =
   "<array id='1' length='12' class='java.lang.String'/>";
   
   private static final String OBJECT =
   "<array id='1' class='java.lang.String'/>";
   
   private static final String REFERENCE =
   "<array reference='1' class='java.lang.String'/>";
   
   private static class Entry implements Type {     
      private final Class type;    
      public Entry(Class type) {
         this.type = type;
      }
      public <T extends Annotation> T getAnnotation(Class<T> type) {
         return null;
      }
      public Class getType() {
         return type;
      }
   }
   
   public void testArray() throws Exception {
      Map map = new HashMap();
      StringReader reader = new StringReader(ARRAY);
      CycleStrategy strategy = new CycleStrategy();
      InputNode event = NodeBuilder.read(reader);
      NodeMap attributes = event.getAttributes();
      Value value = strategy.read(new Entry(String[].class), attributes, map);
      
      assertEquals(12, value.getLength());
      assertEquals(null, value.getValue());
      assertEquals(String.class, value.getType());
      assertEquals(false, value.isReference());
   }
   
   public void testObject() throws Exception {
      Map map = new HashMap();
      StringReader reader = new StringReader(OBJECT);
      CycleStrategy strategy = new CycleStrategy();
      InputNode event = NodeBuilder.read(reader);
      NodeMap attributes = event.getAttributes();
      Value value = strategy.read(new Entry(String.class), attributes, map);
      
      assertEquals(0, value.getLength());
      assertEquals(null, value.getValue());
      assertEquals(String.class, value.getType());
      assertEquals(false, value.isReference());     
   }
   
   public void testReference() throws Exception {
      StringReader reader = new StringReader(REFERENCE);
      Contract contract = new Contract("id", "reference", "class", "length");
      ReadGraph graph = new ReadGraph(contract, new Loader());
      InputNode event = NodeBuilder.read(reader);
      NodeMap attributes = event.getAttributes();
      
      graph.put("1", "first text");
      graph.put("2", "second text");
      graph.put("3", "third text");
      
      Value value = graph.read(new Entry(String.class), attributes);
      
      assertEquals(0, value.getLength());
      assertEquals("first text", value.getValue());
      assertEquals(String.class, value.getType());
      assertEquals(true, value.isReference());     
   }
}
