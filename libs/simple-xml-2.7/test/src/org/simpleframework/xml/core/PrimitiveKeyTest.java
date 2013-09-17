package org.simpleframework.xml.core;

import java.io.PrintWriter;
import java.lang.annotation.Annotation;

import junit.framework.TestCase;

import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.strategy.TreeStrategy;
import org.simpleframework.xml.stream.NodeBuilder;
import org.simpleframework.xml.stream.OutputNode;

public class PrimitiveKeyTest extends TestCase {
   
   private static class MockElementMap implements ElementMap {

      private boolean attribute; 
      private boolean data;
      private String entry;
      private boolean inline;
      private String key;
      private Class keyType;
      private String name;
      private boolean required;
      private String value;
      private Class valueType;
      
      public MockElementMap(
         boolean attribute, 
         boolean data, 
         String entry, 
         boolean inline,
         String key,
         Class keyType,
         String name,
         boolean required,
         String value,
         Class valueType)
      {
         this.attribute = attribute; 
         this.data = data;
         this.entry = entry;
         this.inline = inline;
         this.key = key;
         this.keyType = keyType;
         this.name = name;
         this.required = required;
         this.value = value;
         this.valueType = valueType;
      }
      
      public boolean empty() {
         return true;
      }
      
      public boolean attribute() {
         return attribute;
      }

      public boolean data() {
         return data;
      }

      public String entry() {
         return entry;
      }

      public boolean inline() {
         return inline;
      }

      public String key() {
         return key;
      }

      public Class keyType() {
         return keyType;
      }

      public String name() {
         return name;
      }

      public boolean required() {
         return required;
      }

      public String value() {
         return value;
      }
      
      public double since() {
         return 1.0;
      }

      public Class valueType() {
         return valueType;
      }

      public Class<? extends Annotation> annotationType() {
         return ElementMap.class;
      }
   }
   
   private static class PrimitiveType {

      private MockElementMap map;
      private String string;
      private int number;
      private byte octet;      
      
      public PrimitiveType(MockElementMap map) {
         this.map = map;
      }
      
      public Contact getString() throws Exception {
         return new FieldContact(PrimitiveType.class.getDeclaredField("string"), map,new Annotation[0]);      
      }
      
      public Contact getNumber() throws Exception {
         return new FieldContact(PrimitiveType.class.getDeclaredField("number"), map,new Annotation[0]);      
      }
      
      public Contact getOctet() throws Exception {
         return new FieldContact(PrimitiveType.class.getDeclaredField("octet"), map,new Annotation[0]);      
      }
   }
   
   public void testInlineString() throws Exception 
   {
      Source source = new Source(new TreeStrategy(), new Support(), new Session());
      MockElementMap map = new MockElementMap(true, // attribute
                                              false, // data
                                              "entry", // entry 
                                              true,  // inline
                                              "key", // key
                                              String.class, // keyType
                                              "name", // name
                                              true, // required
                                              "value", // value
                                              String.class); // valueType
      PrimitiveType type = new PrimitiveType(map);
      Contact string = type.getString();
      Entry entry = new Entry(string, map);
      PrimitiveKey value = new PrimitiveKey(source, entry, new ClassType(String.class));
      OutputNode node = NodeBuilder.write(new PrintWriter(System.out));
      
      value.write(node.getChild("inlineString"), "example");
      node.commit();
   }
   
   public void testNotInlineString() throws Exception 
   {
      Source source = new Source(new TreeStrategy(), new Support(), new Session());
      MockElementMap map = new MockElementMap(false, // attribute
                                              false, // data
                                              "entry", // entry 
                                              true,  // inline
                                              "key", // key
                                              String.class, // keyType
                                              "name", // name
                                              true, // required
                                              "value", // value
                                              String.class); // valueType
      PrimitiveType type = new PrimitiveType(map);
      Contact string = type.getString();
      Entry entry = new Entry(string, map);
      PrimitiveKey value = new PrimitiveKey(source, entry, new ClassType(String.class));
      OutputNode node = NodeBuilder.write(new PrintWriter(System.out));
      
      value.write(node.getChild("notInlineString"), "example");
      node.commit();            
   }
   
   public void testNoAttributeString() throws Exception 
   {
      Source source = new Source(new TreeStrategy(), new Support(), new Session());
      MockElementMap map = new MockElementMap(false, // attribute
                                              false, // data
                                              "entry", // entry 
                                              true,  // inline
                                              "", // key
                                              String.class, // keyType
                                              "name", // name
                                              true, // required
                                              "value", // value
                                              String.class); // valueType
      PrimitiveType type = new PrimitiveType(map);
      Contact string = type.getString();
      Entry entry = new Entry(string, map);
      PrimitiveKey value = new PrimitiveKey(source, entry, new ClassType(String.class));
      OutputNode node = NodeBuilder.write(new PrintWriter(System.out));
      
      value.write(node.getChild("noAttributeString"), "example");
      node.commit();
   }
   
   public void testAttributeNoKeyString() throws Exception 
   {
      Source source = new Source(new TreeStrategy(), new Support(), new Session());
      MockElementMap map = new MockElementMap(true, // attribute
                                              false, // data
                                              "entry", // entry 
                                              true,  // inline
                                              "", // key
                                              String.class, // keyType
                                              "name", // name
                                              true, // required
                                              "value", // value
                                              String.class); // valueType
      PrimitiveType type = new PrimitiveType(map);
      Contact string = type.getString();
      Entry entry = new Entry(string, map);
      PrimitiveKey value = new PrimitiveKey(source, entry, new ClassType(String.class));
      OutputNode node = NodeBuilder.write(new PrintWriter(System.out));
      
      value.write(node.getChild("attributeNoKeyString"), "example");
      node.commit();
   }
}
