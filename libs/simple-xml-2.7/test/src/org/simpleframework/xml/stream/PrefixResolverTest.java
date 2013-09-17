package org.simpleframework.xml.stream;

import java.io.StringWriter;

import org.simpleframework.xml.ValidationTestCase;

public class PrefixResolverTest extends ValidationTestCase {
   
   public void testPrefixResolver() throws Exception {
      StringWriter writer = new StringWriter();
      OutputNode node = NodeBuilder.write(writer);
      
      // <root xmlns="ns1">
      OutputNode root = node.getChild("root");
      root.setReference("ns1");
      root.getNamespaces().setReference("ns1", "n");
      
      // <child xmlns="ns2">
      OutputNode child = root.getChild("child");
      child.setReference("ns2");
      child.getNamespaces().setReference("ns2", "n");

      // <grandchild xmlns="ns1">
      OutputNode grandchild = child.getChild("grandchild");
      grandchild.setReference("ns1");
      grandchild.getNamespaces().setReference("ns1", "n");
      
      root.commit();
      
      String text = writer.toString();
      System.out.println(text);
   }
}
