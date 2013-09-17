package org.simpleframework.xml.stream;

import java.io.StringReader;
import java.io.StringWriter;

import org.simpleframework.xml.ValidationTestCase;

public class NamespaceMapTest extends ValidationTestCase {

   private static final String SOURCE =
   "<root a:name='value' xmlns:a='http://www.domain.com/a'>\n" +
   "   <a:child>this is the child</a:child>\n" +
   "</root>";
   
   public void testInputNode() throws Exception {
      StringReader reader = new StringReader(SOURCE);
      InputNode node = NodeBuilder.read(reader);
      NodeMap<InputNode> map = node.getAttributes();
      InputNode attr = map.get("name");
      
      assertEquals("value", attr.getValue());
      assertEquals("a", attr.getPrefix());
      assertEquals("http://www.domain.com/a", attr.getReference());
      
      InputNode child = node.getNext();
      
      assertEquals("this is the child", child.getValue());
      assertEquals("a", child.getPrefix());
      assertEquals("http://www.domain.com/a", child.getReference());
   }
   
   public void testOutputNode() throws Exception {
      StringWriter out = new StringWriter();           
      OutputNode top = NodeBuilder.write(out);
      OutputNode root = top.getChild("root");
      NamespaceMap map = root.getNamespaces();
      
      root.setReference("http://www.sun.com/jsp");
      map.setReference("http://www.w3c.com/xhtml", "xhtml");
      map.setReference("http://www.sun.com/jsp", "jsp");
      
      OutputNode child = root.getChild("child");
     
      child.setAttribute("name.1", "1");
      child.setAttribute("name.2", "2");
     
      OutputNode attribute = child.getAttributes().get("name.1");
      
      attribute.setReference("http://www.w3c.com/xhtml");
      
      OutputNode otherChild = root.getChild("otherChild");
      
      otherChild.setAttribute("name.a", "a");
      otherChild.setAttribute("name.b", "b");
      
      map = otherChild.getNamespaces();
      map.setReference("http://www.w3c.com/xhtml", "ignore");
      
      OutputNode yetAnotherChild = otherChild.getChild("yetAnotherChild");
      
      yetAnotherChild.setReference("http://www.w3c.com/xhtml");
      yetAnotherChild.setValue("example text for yet another namespace");
      
      OutputNode finalChild = otherChild.getChild("finalChild");
      
      map = finalChild.getNamespaces();
      map.setReference("http://www.w3c.com/anonymous");
      
      finalChild.setReference("http://www.w3c.com/anonymous");
      
      OutputNode veryLastChild = finalChild.getChild("veryLastChild");
      
      map = veryLastChild.getNamespaces();
      map.setReference("");
      
      OutputNode veryVeryLastChild = veryLastChild.getChild("veryVeryLastChild");
      
      map = veryVeryLastChild.getNamespaces();
      map.setReference("");
      
      veryVeryLastChild.setReference("");
      veryVeryLastChild.setValue("very very last child");
      
      OutputNode otherVeryVeryLastChild = veryLastChild.getChild("otherVeryVeryLastChild");
      
      // Problem here with anonymous namespace
      otherVeryVeryLastChild.setReference("http://www.w3c.com/anonymous");
      otherVeryVeryLastChild.setValue("other very very last child");
      
      OutputNode yetAnotherVeryVeryLastChild = veryLastChild.getChild("yetAnotherVeryVeryLastChild");
      
      yetAnotherVeryVeryLastChild.setReference("http://www.w3c.com/xhtml");
      yetAnotherVeryVeryLastChild.setValue("yet another very very last child");
    
      root.commit();
      validate(out.toString());
      
   }
}
