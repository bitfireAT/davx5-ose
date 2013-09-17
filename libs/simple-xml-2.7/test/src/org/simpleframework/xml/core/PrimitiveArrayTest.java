package org.simpleframework.xml.core;

import java.io.StringReader;

import junit.framework.TestCase;

import org.simpleframework.xml.strategy.TreeStrategy;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeBuilder;

public class PrimitiveArrayTest extends TestCase {
   
   public static final String ZERO =
   "<array length='0' class='java.lang.String'/>";
   
   public static final String TWO =
    "<array length='2' class='java.lang.String'>"+
    "  <entry>one</entry>" +
    "  <entry>two</entry>" +
    "</array>"; 
   
   public void testZero() throws Exception {
      Context context = new Source(new TreeStrategy(), new Support(), new Session());
      PrimitiveArray primitive = new PrimitiveArray(context, new ClassType(String[].class), new ClassType(String.class), "entry");
      InputNode node = NodeBuilder.read(new StringReader(ZERO));
      Object value = primitive.read(node);
      
      assertEquals(value.getClass(), String[].class);
      
      InputNode newNode = NodeBuilder.read(new StringReader(ZERO));
      
      assertTrue(primitive.validate(newNode)); 
   }
   
   public void testTwo() throws Exception {
      Context context = new Source(new TreeStrategy(), new Support(), new Session());
      PrimitiveArray primitive = new PrimitiveArray(context, new ClassType(String[].class), new ClassType(String.class), "entry");
      InputNode node = NodeBuilder.read(new StringReader(TWO));
      Object value = primitive.read(node);
      
      assertEquals(value.getClass(), String[].class);
      
      String[] list = (String[]) value;
      
      assertEquals(list.length, 2);
      assertEquals(list[0], "one");
      assertEquals(list[1], "two");
      
      InputNode newNode = NodeBuilder.read(new StringReader(TWO));
      
      assertTrue(primitive.validate(newNode)); 
   }
}
