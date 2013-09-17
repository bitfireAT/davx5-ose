package org.simpleframework.xml.strategy;

import java.io.StringWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Map;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeMap;
import org.simpleframework.xml.stream.OutputNode;

public class AnnotationTypeTest extends ValidationTestCase {
   
   private static final String SOURCE = 
   "<?xml version=\"1.0\"?>\n"+
   "<annotationExample age='10'>\n"+
   "   <name key='name'>John Doe</name>\n"+
   "</annotationExample>";
   
   @Retention(RetentionPolicy.RUNTIME)
   private static @interface Component {
      public String name();
   }
   
   @Root
   private static class AnnotationExample {
      
      @Component(name="name")
      @Element
      private String name;
      
      @Component(name="age")
      @Attribute
      private int age;
      
      public AnnotationExample(@Element(name="name") String name, @Attribute(name="age") int age) {
         this.name = name;
         this.age = age;
      }
   }
   
   
   private static class AnnotationStrategy implements Strategy {
      private static final String KEY = "key";
      private final Strategy strategy;
      
      private AnnotationStrategy() {
         this.strategy = new TreeStrategy();
      }
      
      public Value read(Type type, NodeMap<InputNode> node, Map map) throws Exception {
         Component component = type.getAnnotation(Component.class);
         
         if(component != null) {
            String name = component.name();
            InputNode value = node.get(KEY);
            
            if(!value.getValue().equals(name)) {
               throw new IllegalStateException("Component name incorrect, expected '"+name+"' but was '"+value.getValue()+"'");
            }
         }
         return strategy.read(type, node, map);
      }

      public boolean write(Type type, Object value, NodeMap<OutputNode> node, Map map) throws Exception {
         Component component = type.getAnnotation(Component.class);
         
         if(component != null) {
            String name = component.name();
         
            if(name != null) {
               node.put(KEY, name);
            }
         }
         return strategy.write(type, value, node, map);
      }
   }
   
   public void testAnnotationType() throws Exception {
      Strategy strategy = new AnnotationStrategy();
      Persister persister = new Persister(strategy);
      StringWriter writer = new StringWriter();
      AnnotationExample example = persister.read(AnnotationExample.class, SOURCE);
      
      persister.write(example, writer);
      
      String text = writer.toString();
      
      assertElementHasAttribute(text, "/annotationExample", "age", "10");
      assertElementHasAttribute(text, "/annotationExample/name", "key", "name");
      
      AnnotationExample result = persister.read(AnnotationExample.class, text);
      
      assertEquals(example.name, result.name);
      assertEquals(example.age, result.age);
      
      validate(result, persister);
   }

}
