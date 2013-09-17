package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.strategy.Type;
import org.simpleframework.xml.strategy.Visitor;
import org.simpleframework.xml.strategy.VisitorStrategy;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeMap;
import org.simpleframework.xml.stream.OutputNode;


public class ElementsStrategyCallbackTest extends ValidationTestCase {
 
   @Default(required=false)
   private static class Example {
      private double d;
      private int i;
      private float f;
      private String s;
      private byte b;
   }
   
   private static class TypeDecorator implements Visitor {
      private final Map<Class, String> binding;
      public TypeDecorator() {
         this.binding = new HashMap<Class, String>();
      }
      public void register(Class type, String name) {
         binding.put(type, name);
      }
      public void read(Type type, NodeMap<InputNode> node) throws Exception {
         InputNode value = node.get("type");
         System.out.println(value);
      }
      public void write(Type type, NodeMap<OutputNode> node) throws Exception {
         Class key = type.getType();
         String name = binding.get(key);
         if(name != null) {
            node.put("type", name);
         }
      }
 
   }

   public void testTypeCallback() throws Exception {
      TypeDecorator decorator = new TypeDecorator();
      Strategy strategy = new VisitorStrategy(decorator);
      Persister persister = new Persister(strategy);
      decorator.register(Double.class, "double");
      decorator.register(Integer.class, "int");
      decorator.register(Float.class, "float");
      decorator.register(Byte.class, "byte");
      decorator.register(String.class, "string");
      Example example = new Example();
      example.s = "text";
      StringWriter out = new StringWriter();
      persister.write(example, out);
      Example other = persister.read(Example.class, out.toString());
      assertEquals(other.d, example.d);
      System.out.println(out);
   }
}
