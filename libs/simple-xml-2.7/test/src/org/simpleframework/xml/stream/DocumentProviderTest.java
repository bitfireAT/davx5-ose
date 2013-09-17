package org.simpleframework.xml.stream;

import java.io.StringReader;

import org.simpleframework.xml.ValidationTestCase;

public class DocumentProviderTest extends ValidationTestCase {

   private static final String SOURCE =
      "<root name='top'>\n"+
      "    <!-- child node -->\n"+
      "    <child a='A' b='B'>\n"+
      "        <leaf>leaf node</leaf>\n"+
      "    </child>\n"+
      "</root>";

      
      public void testReader() throws Exception {
         Provider provider = new DocumentProvider();
         StringReader source = new StringReader(SOURCE);
         EventReader reader = provider.provide(source);
         
         assertEquals(reader.peek().getName(), "root");
         assertEquals(reader.next().getName(), "root");
         assertTrue(reader.peek().isText());
         assertTrue(reader.next().isText());
         
         while(reader.peek().isText()) {
            assertTrue(reader.next().isText()); // remove text from the document
         }
         assertEquals(reader.peek().getName(), "child");
         assertEquals(reader.next().getName(), "child"); 
         assertTrue(reader.peek().isText());
         assertTrue(reader.next().isText());
         
         while(reader.peek().isText()) {
            assertTrue(reader.next().isText()); // remove text from the document
         }
         assertEquals(reader.peek().getName(), "leaf");
         assertEquals(reader.next().getName(), "leaf"); 
         assertTrue(reader.peek().isText());
         assertEquals(reader.peek().getValue(), "leaf node");
         assertEquals(reader.next().getValue(), "leaf node");
         assertTrue(reader.next().isEnd());
         
         while(reader.peek().isText()) {
            assertTrue(reader.next().isText()); // remove text from the document
         }
         assertTrue(reader.next().isEnd()); 
         
         while(reader.peek().isText()) {
            assertTrue(reader.next().isText()); // remove text from the document
         }
      }

   
}
