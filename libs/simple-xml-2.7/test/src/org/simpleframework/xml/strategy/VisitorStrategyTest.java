package org.simpleframework.xml.strategy;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.PersistenceException;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeMap;
import org.simpleframework.xml.stream.OutputNode;

public class VisitorStrategyTest extends ValidationTestCase {
   
   @Root
   @Default
   private static class VisitorExample {
      private List<String> items;
      private Map<String, String> map;
      public VisitorExample() {
         this.map = new HashMap<String, String>();
         this.items = new ArrayList<String>();
      }
      public void put(String name, String value) {
         map.put(name, value);
      }
      public void add(String value) {
         items.add(value);
      }
   }
   
   public void testStrategy() throws Exception {
      Visitor visitor = new ClassToNamespaceVisitor();
      Strategy strategy = new VisitorStrategy(visitor);
      Persister persister = new Persister(strategy);
      VisitorExample item = new VisitorExample();
      StringWriter writer = new StringWriter();
      
      item.put("1", "ONE");
      item.put("2", "TWO");
      item.add("A");
      item.add("B");
      
      persister.write(item, writer);
      
      String text = writer.toString();
      System.out.println(text);
      
      VisitorExample recover = persister.read(VisitorExample.class, text);
      
      assertTrue(recover.map.containsKey("1"));
      assertTrue(recover.map.containsKey("2"));
      assertTrue(recover.items.contains("A"));
      assertTrue(recover.items.contains("B"));  
      
      validate(recover, persister);
   }

}
