package org.simpleframework.xml.core;

import java.io.StringReader;
import java.util.List;
import java.util.Vector;

import junit.framework.TestCase;

import org.simpleframework.xml.strategy.TreeStrategy;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeBuilder;

public class PrimitiveListTest extends TestCase {
   
   public static final String TWO =
    "<array class='java.util.Vector'>"+
    "  <entry>one</entry>" +
    "  <entry>two</entry>" +
    "</array>"; 
   
   public void testTwo() throws Exception {
      Context context = new Source(new TreeStrategy(), new Support(), new Session());
      PrimitiveList primitive = new PrimitiveList(context, new ClassType(List.class), new ClassType(String.class), "entry");
      InputNode node = NodeBuilder.read(new StringReader(TWO));
      Object value = primitive.read(node);
      
      assertEquals(value.getClass(), Vector.class);
      
      Vector vector = (Vector) value;
      
      assertEquals(vector.get(0), "one");
      assertEquals(vector.get(1), "two");
      
      InputNode newNode = NodeBuilder.read(new StringReader(TWO));
      
      assertTrue(primitive.validate(newNode)); 
   }
}
