package org.simpleframework.xml.stream;

import java.io.Reader;
import java.io.StringReader;

import junit.framework.TestCase;

public class NodeBuilderTest extends TestCase {
    
    private static final String SOURCE = 
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
   
    public void testNodeAdapter() throws Exception {         
       Reader reader = new StringReader(SOURCE);
       InputNode event = NodeBuilder.read(reader);
       
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
}
