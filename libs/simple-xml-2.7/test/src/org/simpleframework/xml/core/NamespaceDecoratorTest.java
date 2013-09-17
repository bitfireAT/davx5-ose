package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.lang.annotation.Annotation;

import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.NamespaceDecorator;
import org.simpleframework.xml.stream.NodeBuilder;
import org.simpleframework.xml.stream.OutputNode;

public class NamespaceDecoratorTest extends ValidationTestCase {
   
   private static class MockNamespace implements Namespace {
      
      private final String prefix;
      private final String reference;
      
      public MockNamespace(String prefix, String reference) {
         this.prefix = prefix;
         this.reference = reference;
      }
      
      public String reference() {
         return reference;
      }
      
      public String prefix() {
         return prefix;
      }

      public Class<? extends Annotation> annotationType() {
         return Namespace.class;
      }
   }
   
   public void testQualifier() throws Exception {
      NamespaceDecorator global = new NamespaceDecorator();
      NamespaceDecorator qualifier = new NamespaceDecorator();
      NamespaceDecorator attribute = new NamespaceDecorator();
      
      global.add(new MockNamespace("global", "http://www.domain.com/global")); 
      qualifier.add(new MockNamespace("a", "http://www.domain.com/a"));
      qualifier.add(new MockNamespace("b", "http://www.domain.com/b"));
      qualifier.add(new MockNamespace("c", "http://www.domain.com/c"));
      attribute.add(new MockNamespace("d", "http://www.domain.com/d"));
      
      global.set(new MockNamespace("first", "http://www.domain.com/ignore"));
      qualifier.set(new MockNamespace("a", "http://www.domain.com/a"));
      attribute.set(new MockNamespace("b", "http://www.domain.com/b"));

      StringWriter out = new StringWriter(); 
      
      OutputNode top = NodeBuilder.write(out);
      OutputNode root = top.getChild("root");
      
      root.setAttribute("version", "1.0");
      qualifier.decorate(root, global);
      
      OutputNode child = root.getChild("child");
      child.setAttribute("name", "John Doe");
      
      OutputNode name = child.getAttributes().get("name");
      attribute.decorate(name);
      
      OutputNode grandChild = child.getChild("grandChild");
      grandChild.setValue("this is the grand child");
      
      root.commit();
      validate(out.toString());     
   }
}
