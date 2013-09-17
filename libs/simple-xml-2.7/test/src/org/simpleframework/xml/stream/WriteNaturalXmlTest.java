package org.simpleframework.xml.stream;

import java.io.StringReader;
import java.io.StringWriter;

import junit.framework.TestCase;

public class WriteNaturalXmlTest extends TestCase {

   public void testWriteXml() throws Exception {
      StringWriter writer = new StringWriter();
      OutputNode node = NodeBuilder.write(writer);
      OutputNode root = node.getChild("root");
      
      root.setValue("xxx");
      
      OutputNode child = root.getChild("child");
      
      root.setValue("222");
      
      OutputNode child2 = root.getChild("child2");
      root.commit();
      System.err.println(writer);
      
      StringReader reader = new StringReader(writer.toString());
      InputNode input = NodeBuilder.read(reader);
      
      assertEquals(input.getValue().trim(), "xxx");
      
      InputNode next = input.getNext("child");
      
      assertEquals(input.getValue().trim(), "222");
   }
}
