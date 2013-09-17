package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.strategy.ClassToNamespaceVisitor;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.strategy.Visitor;
import org.simpleframework.xml.strategy.VisitorStrategy;

public class SuperTypeTest extends TestCase {
   
   public static interface SuperType {
      public void doSomething();
   }

   @Root
   public static class SubType1 implements SuperType {   
      @Element
      private String text;
      public void doSomething() {
         System.out.println("SubType1: " + this);
      }
      public String toString() {
         return text;
      }  
   }
   
   @Root
   public static class SubType2 implements SuperType {
      @Element
      private SuperType superType;
      public void doSomething() {
         System.out.println("SubType2: " + this);
      }
      public String toString() {
         return "Inner: " + superType.toString();
      }
   }

   @Root(name="objects")
   public static class MyMap {    
      @ElementMap(entry="object", key="key", attribute=true, inline=true)
      private Map<String, SuperType> map = new HashMap<String, SuperType>();
      public Map<String, SuperType> getInternalMap() {
         return map;
      }  
   }

   public void testSuperType() throws Exception {
      Map<String, String> clazMap = new HashMap<String, String> ();
      
      clazMap.put("subtype1", SubType1.class.getName());
      clazMap.put("subtype2", SubType2.class.getName());
      
      Visitor visitor = new ClassToNamespaceVisitor(false);
      Strategy strategy = new VisitorStrategy(visitor);
      Serializer serializer = new Persister(strategy);
      MyMap map = new MyMap();
      SubType1 subtype1 = new SubType1();
      SubType2 subtype2 = new SubType2();
      StringWriter writer = new StringWriter();
      
      subtype1.text = "subtype1";
      subtype2.superType = subtype1;
      
      map.getInternalMap().put("one", subtype1);
      map.getInternalMap().put("two", subtype2);
   
      serializer.write(map, writer); 
      serializer.write(map, System.out); 
      serializer.read(MyMap.class, writer.toString());
   }
}
