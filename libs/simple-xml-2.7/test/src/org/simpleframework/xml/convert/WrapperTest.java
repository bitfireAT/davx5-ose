package org.simpleframework.xml.convert;

import java.io.StringWriter;

import junit.framework.TestCase;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

public class WrapperTest extends TestCase {
   
   private static class Wrapper {
      private final Object value;
      public Wrapper(Object value) {
         this.value = value;
      }
      public Object get(){
         return value;
      }
   }
   
   private static class WrapperConverter implements Converter<Wrapper> {
      private final Serializer serializer;
      public WrapperConverter(Serializer serializer) {
         this.serializer = serializer;
      }
      public Wrapper read(InputNode node) throws Exception {
         InputNode type = node.getAttribute("type");
         InputNode child = node.getNext();
         String className = type.getValue();
         Object value = null;
         if(child != null) {
            value = serializer.read(Class.forName(className), child);
         }
         return new Wrapper(value);
      }
      public void write(OutputNode node, Wrapper wrapper) throws Exception {
         Object value = wrapper.get();
         Class type = value.getClass();
         String className = type.getName();
         node.setAttribute("type", className);
         serializer.write(value, node);
      }
   } 
   
   @Root
   @Default(required=false)
   private static class Entry {
      private String name;
      private String value;
      public Entry(@Element(name="name", required=false) String name, @Element(name="value", required=false) String value){
         this.name = name;
         this.value = value;
      }
   }
   
   @Root
   @Default(required=false)
   private static class WrapperExample {
      @Convert(WrapperConverter.class)
      private Wrapper wrapper;
      public WrapperExample(@Element(name="wrapper", required=false) Wrapper wrapper) {
         this.wrapper = wrapper;
      }
   }

   public void testWrapper() throws Exception{
      Registry registry = new Registry();
      Strategy strategy = new RegistryStrategy(registry);
      Serializer serializer = new Persister(strategy);
      Entry entry = new Entry("name", "value");
      Wrapper wrapper = new Wrapper(entry);
      WrapperExample example = new WrapperExample(wrapper);
      WrapperConverter converter = new WrapperConverter(serializer);
      StringWriter writer = new StringWriter();
      registry.bind(Wrapper.class, converter);
      serializer.write(example, writer);
      serializer.read(WrapperExample.class, writer.toString());
      System.err.println(writer.toString());
   }
}
