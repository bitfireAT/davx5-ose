
package org.simpleframework.xml.stream;

import junit.framework.TestCase;
import java.io.StringReader;

import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeBuilder;
import org.simpleframework.xml.stream.NodeMap;

public class NodeReaderTest extends TestCase {
        
   private static final String SMALL_SOURCE =
   "<?xml version=\"1.0\"?>\n"+
   "<override id='12' flag='true'>\n"+
   "   <text>entry text</text>  \n\r"+
   "   <name>some name</name> \n"+
   "   <third>added to schema</third>\n"+
   "</override>";
   
   private static final String LARGE_SOURCE = 
    "<?xml version='1.0'?>\n" +
    "<root version='2.1' id='234'>\n" +
    "   <list type='sorted'>\n" +
    "      <entry name='1'>\n" +
    "         <value>value 1</value>\n" +
    "      </entry>\n" +
    "      <entry name='2'>\n" +
    "         <value>value 2</value>\n" +
    "      </entry>\n" +
    "      <entry name='3'>\n" +
    "         <value>value 3</value>\n" +
    "      </entry>\n" +                   
    "   </list>\n" +
    "   <object name='name'>\n" +
    "      <integer>123</integer>\n" +
    "      <object name='key'>\n" +
    "         <integer>12345</integer>\n" +
    "      </object>\n" +
    "   </object>\n" +
    "</root>";
   
   public static final String EMPTY_SOURCE =
   "<root>\r\n" +
   "   <empty/>\r\n" +
   "   <notEmpty name='foo'/>\r\n" +
   "   <empty></empty>\r\n" +
   "</root>";
   
   public void testEmptySource() throws Exception {
      InputNode event = NodeBuilder.read(new StringReader(EMPTY_SOURCE));

      assertTrue(event.isRoot());
      assertFalse(event.isEmpty());
      assertEquals("root", event.getName());
      
      InputNode child  = event.getNext();
      
      assertTrue(child.isEmpty());
      assertEquals("empty", child.getName());
      
      child = event.getNext();
      
      assertFalse(child.isEmpty());
      assertEquals("notEmpty", child.getName());
      assertEquals("foo", child.getAttribute("name").getValue());   
      
      child = event.getNext();
      
      assertTrue(child.isEmpty());
      assertEquals("empty", child.getName());
   }
   
   public void testSmallSource() throws Exception {
      InputNode event = NodeBuilder.read(new StringReader(SMALL_SOURCE));

      assertTrue(event.isRoot());
      assertEquals("override", event.getName());         
      assertEquals("12", event.getAttribute("id").getValue());
      assertEquals("true", event.getAttribute("flag").getValue());

      NodeMap list = event.getAttributes();

      assertEquals("12", list.get("id").getValue());
      assertEquals("true", list.get("flag").getValue());

      InputNode text = event.getNext();
      
      assertFalse(text.isRoot());
      assertTrue(event.isRoot());
      assertEquals("text", text.getName());
      assertEquals("entry text", text.getValue());
      assertEquals(null, text.getNext());
      
      InputNode name = event.getNext();
      
      assertFalse(name.isRoot());
      assertEquals("name", name.getName());
      assertEquals("some name", name.getValue());
      assertEquals(null, name.getNext());
      assertEquals(null, text.getNext());
      
      InputNode third = event.getNext();
      
      assertTrue(event.isRoot());
      assertFalse(third.isRoot());
      assertEquals("third", third.getName());
      assertEquals("text", text.getName());
      assertEquals(null, text.getNext());
      assertEquals("added to schema", third.getValue());
      assertEquals(null, event.getNext());
   }   

   
   public void testLargeSource() throws Exception {
      InputNode event = NodeBuilder.read(new StringReader(LARGE_SOURCE));

      assertTrue(event.isRoot());
      assertEquals("root", event.getName());         
      assertEquals("2.1", event.getAttribute("version").getValue());
      assertEquals("234", event.getAttribute("id").getValue());

      NodeMap attrList = event.getAttributes();

      assertEquals("2.1", attrList.get("version").getValue());
      assertEquals("234", attrList.get("id").getValue());

      InputNode list = event.getNext();

      assertFalse(list.isRoot());
      assertEquals("list", list.getName());
      assertEquals("sorted", list.getAttribute("type").getValue());
      
      InputNode entry = list.getNext();
      InputNode value = list.getNext(); // same as entry.getNext()
      
      assertEquals("entry", entry.getName());
      assertEquals("1", entry.getAttribute("name").getValue());
      assertEquals("value", value.getName());
      assertEquals("value 1", value.getValue());
      assertEquals(null, value.getAttribute("name"));
      assertEquals(null, entry.getNext());
      assertEquals(null, value.getNext());
      
      entry = list.getNext();
      value = entry.getNext(); // same as list.getNext()
      
      assertEquals("entry", entry.getName());
      assertEquals("2", entry.getAttribute("name").getValue());
      assertEquals("value", value.getName());
      assertEquals("value 2", value.getValue());
      assertEquals(null, value.getAttribute("name"));
      assertEquals(null, entry.getNext());

      entry = list.getNext();
      value = entry.getNext(); // same as list.getNext()
      
      assertEquals("entry", entry.getName());
      assertEquals("3", entry.getAttribute("name").getValue());
      assertEquals("value", value.getName());
      assertEquals("value 3", value.getValue());
      assertEquals(null, value.getAttribute("name"));
      assertEquals(null, entry.getNext());
      assertEquals(null, list.getNext());

      InputNode object = event.getNext();
      InputNode integer = event.getNext(); // same as object.getNext()

      assertEquals("object", object.getName());
      assertEquals("name", object.getAttribute("name").getValue());
      assertEquals("integer", integer.getName());
      assertEquals("123", integer.getValue());
      
      object = object.getNext(); // same as event.getNext()
      integer = object.getNext();

      assertEquals("object", object.getName());
      assertEquals("key", object.getAttribute("name").getValue());
      assertEquals("integer", integer.getName());
      assertEquals("12345", integer.getValue());
   }           

   public void testSkip() throws Exception {
      InputNode event = NodeBuilder.read(new StringReader(LARGE_SOURCE));

      assertTrue(event.isRoot());
      assertEquals("root", event.getName());         
      assertEquals("2.1", event.getAttribute("version").getValue());
      assertEquals("234", event.getAttribute("id").getValue());

      NodeMap attrList = event.getAttributes();

      assertEquals("2.1", attrList.get("version").getValue());
      assertEquals("234", attrList.get("id").getValue());

      InputNode list = event.getNext();

      assertFalse(list.isRoot());
      assertEquals("list", list.getName());
      assertEquals("sorted", list.getAttribute("type").getValue());
      
      InputNode entry = list.getNext();
      InputNode value = list.getNext(); // same as entry.getNext()
      
      assertEquals("entry", entry.getName());
      assertEquals("1", entry.getAttribute("name").getValue());
      assertEquals("value", value.getName());
      assertEquals("value 1", value.getValue());
      assertEquals(null, value.getAttribute("name"));
      assertEquals(null, entry.getNext());
      assertEquals(null, value.getNext());
      
      entry = list.getNext();
      entry.skip();

      assertEquals(entry.getNext(), null);

      entry = list.getNext();
      value = entry.getNext(); // same as list.getNext()
      
      assertEquals("entry", entry.getName());
      assertEquals("3", entry.getAttribute("name").getValue());
      assertEquals("value", value.getName());
      assertEquals("value 3", value.getValue());
      assertEquals(null, value.getAttribute("name"));
      assertEquals(null, entry.getNext());
      assertEquals(null, list.getNext());

      InputNode object = event.getNext();
      InputNode integer = event.getNext(); // same as object.getNext()

      assertEquals("object", object.getName());
      assertEquals("name", object.getAttribute("name").getValue());
      assertEquals("integer", integer.getName());
      assertEquals("123", integer.getValue());
      
      object = object.getNext(); // same as event.getNext()
      integer = object.getNext();

      assertEquals("object", object.getName());
      assertEquals("key", object.getAttribute("name").getValue());
      assertEquals("integer", integer.getName());
      assertEquals("12345", integer.getValue());
   }
}
