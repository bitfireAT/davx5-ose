package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.strategy.ClassToNamespaceVisitor;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.strategy.Visitor;
import org.simpleframework.xml.strategy.VisitorStrategy;

public class ErasureHandlingTest extends ValidationTestCase {
   
   @Root
   @Default
   private static class ErasureExample<T> {
      private Map<String, T> list = new LinkedHashMap<String, T>();
      private Map<T, T> doubleGeneric = new LinkedHashMap<T, T>();
      public void addItem(String key, T item) {
         list.put(key, item);
      }
      public T getItem(String key){
         return list.get(key);
      }
      public void addDoubleGeneric(T key, T item) {
         doubleGeneric.put(key, item);
      }
   }
   
   @Root
   @Default
   private static class ErasureItem {
      private final String name;
      private final String value;
      public ErasureItem(@Element(name="name") String name, @Element(name="value") String value) {
         this.name = name;
         this.value = value;
      }
   }
   
   @Root
   private static class ErasureWithMapAttributeIllegalExample<T> {
      @ElementMap(attribute=true)
      private Map<T, String> erasedToString = new HashMap<T, String>();
      public void addItem(T key, String value){
         erasedToString.put(key, value);
      }
   }
   
   @Root
   private static class ErasureWithMapInlineValueIsIgnoredExample<T> {
      @ElementMap(attribute=true, inline=true, value="value", key="key")
      private Map<String, T> erasedToString = new LinkedHashMap<String, T>();
      public void addItem(String key, T value){
         erasedToString.put(key, value);
      }
      public T getItem(String key) {
         return erasedToString.get(key);
      }
   }
   
   private static enum ErasureEnum {
      A, B, C, D
   }
   
   public void testErasure() throws Exception {
      Visitor visitor = new ClassToNamespaceVisitor();
      Strategy strategy = new VisitorStrategy(visitor);
      Persister persister = new Persister(strategy);
      ErasureExample<ErasureItem> example = new ErasureExample<ErasureItem>();
      StringWriter writer = new StringWriter();
      
      example.addItem("a", new ErasureItem("A", "1"));
      example.addItem("b", new ErasureItem("B", "2"));
      example.addItem("c", new ErasureItem("C", "3"));
      
      example.addDoubleGeneric(new ErasureItem("1", "1"), new ErasureItem("A", "1"));
      example.addDoubleGeneric(new ErasureItem("2", "2"), new ErasureItem("B", "2"));
      example.addDoubleGeneric(new ErasureItem("3", "3"), new ErasureItem("C", "3"));
      
      persister.write(example, writer);
      String text = writer.toString();
      System.out.println(text);
      
      ErasureExample<ErasureItem> exampleCopy = persister.read(ErasureExample.class, text);
    
      assertEquals(exampleCopy.getItem("a").name, "A");  
      assertEquals(exampleCopy.getItem("b").name, "B");  
      assertEquals(exampleCopy.getItem("c").name, "C");  
      
      validate(example, persister);
   }
   
   public void testPrimitiveErasure() throws Exception {
      Visitor visitor = new ClassToNamespaceVisitor();
      Strategy strategy = new VisitorStrategy(visitor);
      Persister persister = new Persister(strategy);
      ErasureExample<Double> example = new ErasureExample<Double>();
      
      example.addItem("a", 2.0);
      example.addItem("b", 1.2);
      example.addItem("c", 5.4);
      
      example.addDoubleGeneric(7.8, 8.7);
      example.addDoubleGeneric(1.2, 2.1);
      example.addDoubleGeneric(3.1, 1.3);
      
      persister.write(example, System.out);  
      
      validate(example, persister);
   }
   
   public void testEnumErasure() throws Exception {
      Visitor visitor = new ClassToNamespaceVisitor();
      Strategy strategy = new VisitorStrategy(visitor);
      Persister persister = new Persister(strategy);
      ErasureExample<ErasureEnum> example = new ErasureExample<ErasureEnum>();
      
      example.addItem("a", ErasureEnum.A);
      example.addItem("b", ErasureEnum.B);
      example.addItem("c", ErasureEnum.C);
      
      example.addDoubleGeneric(ErasureEnum.A, ErasureEnum.B);
      example.addDoubleGeneric(ErasureEnum.B, ErasureEnum.C);
      example.addDoubleGeneric(ErasureEnum.C, ErasureEnum.D);
      
      persister.write(example, System.out);   
      
      validate(example, persister);
   }
   
   public void testErasureWithMapAttributeIllegalExample() throws Exception {
      Visitor visitor = new ClassToNamespaceVisitor();
      Strategy strategy = new VisitorStrategy(visitor);
      Persister persister = new Persister(strategy);
      ErasureWithMapAttributeIllegalExample<ErasureEnum> example = new ErasureWithMapAttributeIllegalExample<ErasureEnum>();
      boolean failure = false;
      
      example.addItem(ErasureEnum.A, "a");
      example.addItem(ErasureEnum.B, "b");
      example.addItem(ErasureEnum.C, "c");
      
      try {
         persister.write(example, System.out);
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Attribute should not be possible with an erased key", failure);
   }
   
   public void testErasureWithMapInlineValueIsIgnoredExample() throws Exception {
      Persister persister = new Persister();
      ErasureWithMapInlineValueIsIgnoredExample<ErasureItem> example = new ErasureWithMapInlineValueIsIgnoredExample<ErasureItem>();
      StringWriter writer = new StringWriter();
      
      example.addItem("a", new ErasureItem("A", "1"));
      example.addItem("b", new ErasureItem("B", "2"));
      example.addItem("c", new ErasureItem("C", "3"));
      
      persister.write(example, writer);  
      String text = writer.toString();
      System.out.println(text);
      
      assertElementHasAttribute(text, "/erasureWithMapInlineValueIsIgnoredExample/entry[1]", "key", "a");
      assertElementHasAttribute(text, "/erasureWithMapInlineValueIsIgnoredExample/entry[2]", "key", "b");
      assertElementHasAttribute(text, "/erasureWithMapInlineValueIsIgnoredExample/entry[3]", "key", "c");
      
      assertElementHasAttribute(text, "/erasureWithMapInlineValueIsIgnoredExample/entry[1]/value", "class", ErasureItem.class.getName());
      assertElementHasAttribute(text, "/erasureWithMapInlineValueIsIgnoredExample/entry[2]/value", "class", ErasureItem.class.getName());
      assertElementHasAttribute(text, "/erasureWithMapInlineValueIsIgnoredExample/entry[3]/value", "class", ErasureItem.class.getName());
      
      assertElementHasValue(text, "/erasureWithMapInlineValueIsIgnoredExample/entry[1]/value/name", "A");
      assertElementHasValue(text, "/erasureWithMapInlineValueIsIgnoredExample/entry[2]/value/name", "B");
      assertElementHasValue(text, "/erasureWithMapInlineValueIsIgnoredExample/entry[3]/value/name", "C");
      
      assertElementHasValue(text, "/erasureWithMapInlineValueIsIgnoredExample/entry[1]/value/value", "1");
      assertElementHasValue(text, "/erasureWithMapInlineValueIsIgnoredExample/entry[2]/value/value", "2");
      assertElementHasValue(text, "/erasureWithMapInlineValueIsIgnoredExample/entry[3]/value/value", "3");
      
      System.out.println(text);
      
      ErasureWithMapInlineValueIsIgnoredExample<ErasureItem> exampleCopy = persister.read(ErasureWithMapInlineValueIsIgnoredExample.class, text);
      
      assertEquals(exampleCopy.getItem("a").name, "A");
      assertEquals(exampleCopy.getItem("b").name, "B");
      assertEquals(exampleCopy.getItem("c").name, "C");
      assertEquals(exampleCopy.getItem("a").value, "1");
      assertEquals(exampleCopy.getItem("b").value, "2");
      assertEquals(exampleCopy.getItem("c").value, "3");
      
      validate(exampleCopy, persister);
   }
}
