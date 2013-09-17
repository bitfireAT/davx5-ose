package org.simpleframework.xml.stream;

import java.io.StringReader;

import junit.framework.TestCase;

public class NamespaceAttributeTest extends TestCase {
   
   private static final String SOURCE =
   "<root xmlns='default' xmlns:a='A' xmlns:b='B'>" +
   "   <child a:attributeA='valueA' b:attributeB='valueB'>"+
   "      <leaf b:attributeC='c'/>"+
   "   </child>+" +
   "   <a:entry b:attributeD='valueD'/>"+
   "</root>";

   public void testAttributes() throws Exception {
      InputNode root = NodeBuilder.read(new StringReader(SOURCE));
      InputNode child = root.getNext();
      NodeMap<InputNode> map = child.getAttributes();
      
      assertEquals(root.getReference(), "default");
      assertEquals(child.getReference(), "default");
      
      assertEquals(map.get("attributeA").getValue(), "valueA");
      assertEquals(map.get("attributeA").getPrefix(), "a");
      assertEquals(map.get("attributeA").getReference(), "A");
      
      assertEquals(map.get("attributeB").getValue(), "valueB");
      assertEquals(map.get("attributeB").getPrefix(), "b");
      assertEquals(map.get("attributeB").getReference(), "B");
      
      InputNode leaf = child.getNext();
      
      assertEquals(leaf.getReference(), "default");
      assertEquals(leaf.getAttribute("attributeC").getValue(), "c");
      assertEquals(leaf.getAttribute("attributeC").getPrefix(), "b");
      assertEquals(leaf.getAttribute("attributeC").getReference(), "B");
      
      InputNode entry = root.getNext();
      
      assertEquals(entry.getReference(), "A");
      assertEquals(entry.getAttribute("attributeD").getValue(), "valueD");
      assertEquals(entry.getAttribute("attributeD").getPrefix(), "b");
      assertEquals(entry.getAttribute("attributeD").getReference(), "B");
   }
   
}
