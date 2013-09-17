
package org.simpleframework.xml.stream;

import java.io.StringReader;

import junit.framework.TestCase;

public class FloatingTextTest extends TestCase {
   
   public static final String SOURCE =
   "<root>\n" +
   "   Floating text\n"+
   "   <empty/>Some more floating text\n" +
   "   <notEmpty name='foo'/>\n" +
   "   <other>Some other text</other>Following text\n" +
   "</root>";
   
   public static final String COMPLEX =
   "<root>\n" +
   "   First line\n"+
   "   <a>A</a>\n" +
   "   Second line\n"+
   "   <b>B</b>\n" +
   "   Third line\n" +
   "</root>";       
   
   public void testComplexSource() throws Exception {
      InputNode event = NodeBuilder.read(new StringReader(COMPLEX));

      assertTrue(event.isRoot());
      assertFalse(event.isEmpty());
      assertEquals(event.getValue(), "\n   First line\n   ");
      assertEquals("root", event.getName());
      
      InputNode a = event.getNext("a");
      
      assertNotNull(a);
      assertEquals(a.getName(), "a");
      assertEquals(a.getValue(), "A");
      
      InputNode a2 = event.getNext("a");
      
      assertEquals(event.getValue(), "\n   Second line\n   ");
      assertNull(a2);

   }
   
   public void testEmptySource() throws Exception {
      InputNode event = NodeBuilder.read(new StringReader(SOURCE));

      assertTrue(event.isRoot());
      assertFalse(event.isEmpty());
      assertEquals(event.getValue(), "\n   Floating text\n   ");
      assertEquals("root", event.getName());
      
      InputNode child  = event.getNext();
      
      assertTrue(child.isEmpty());
      assertEquals("empty", child.getName());
      assertEquals(event.getValue(), "Some more floating text\n   ");
      
      child = event.getNext();
      
      assertFalse(child.isEmpty());
      assertEquals("notEmpty", child.getName());
      assertEquals("foo", child.getAttribute("name").getValue());   
      
      child = event.getNext();
      
      assertFalse(child.isEmpty());
      assertNull(event.getValue());
      assertEquals(child.getValue(), "Some other text");
      assertTrue(child.isEmpty());
      assertEquals(event.getValue(), "Following text\n");
      assertEquals("other", child.getName());
   }
}
