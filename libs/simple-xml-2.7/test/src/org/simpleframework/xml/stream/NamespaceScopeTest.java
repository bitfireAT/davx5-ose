package org.simpleframework.xml.stream;

import java.io.StringReader;

import org.simpleframework.xml.ValidationTestCase;

public class NamespaceScopeTest extends ValidationTestCase {
   
   private static final String EMPTY_OVERRIDE =
   "<root xmlns='http://www.default.com/'>\n"+ // http://www.default.com/
   "<entry xmlns=''>\n"+ 
   "<p:book xmlns:p='http://www.example.com/book'>\n"+ // http://www.example.com/book
   "<author>saurabh</author>\n"+ // empty 
   "<p:title>simple xml</p:title>\n"+ // http://www.example.com/book
   "<p:isbn>ISB-16728-10</p:isbn>\n"+ // http://www.example.com/book
   "</p:book>\n"+
   "</entry>\n"+
   "</root>";    
   
   private static final String DEFAULT_FIRST = 
   "<root xmlns='http://www.default.com/'>\n"+ // http://www.default.com/
   "<p:book xmlns:p='http://www.example.com/book'>\n"+ // http://www.example.com/book
   "<author>saurabh</author>\n"+ // http://www.default.com/
   "<title>simple xml</title>\n"+ // http://www.default.com/
   "<isbn>ISB-16728-10</isbn>\n"+ // http://www.default.com/
   "</p:book>\n"+
   "</root>";    
   
   public void testEmptyOverride() throws Exception {
      InputNode node = NodeBuilder.read(new StringReader(EMPTY_OVERRIDE));
      String reference = node.getReference();
      String prefix = node.getPrefix();
      
      assertTrue(isEmpty(prefix));
      assertEquals(reference, "http://www.default.com/");
      
      node = node.getNext("entry");
      reference = node.getReference();
      prefix = node.getPrefix();
      
      assertTrue(isEmpty(prefix));
      assertTrue(isEmpty(reference));
      
      node = node.getNext("book");
      reference = node.getReference();
      prefix = node.getPrefix();
      
      assertEquals(prefix, "p");
      assertEquals(reference, "http://www.example.com/book");
      
      InputNode author = node.getNext("author");
      reference = author.getReference();
      prefix = author.getPrefix();
      
      assertTrue(isEmpty(prefix));
      assertTrue(isEmpty(reference));
      
      InputNode title = node.getNext("title");
      reference = title.getReference();
      prefix = title.getPrefix();
      
      assertEquals(prefix, "p");
      assertEquals(reference, "http://www.example.com/book");
      
      InputNode isbn = node.getNext("isbn");
      reference = isbn.getReference();
      prefix = isbn.getPrefix();
      
      assertEquals(prefix, "p");
      assertEquals(reference, "http://www.example.com/book");   
   }
   
   public void testDefaultFirst() throws Exception {
      InputNode node = NodeBuilder.read(new StringReader(DEFAULT_FIRST));
      String reference = node.getReference();
      String prefix = node.getPrefix();
      
      assertTrue(isEmpty(prefix));
      assertEquals(reference, "http://www.default.com/");
      
      node = node.getNext("book");
      reference = node.getReference();
      prefix = node.getPrefix();
      
      assertEquals(prefix, "p");
      assertEquals(reference, "http://www.example.com/book");
      
      InputNode author = node.getNext("author");
      reference = author.getReference();
      prefix = author.getPrefix();
      
      assertTrue(isEmpty(prefix));
      assertEquals(reference, "http://www.default.com/");
      
      InputNode title = node.getNext("title");
      reference = title.getReference();
      prefix = title.getPrefix();
      
      assertTrue(isEmpty(prefix));
      assertEquals(reference, "http://www.default.com/");
      
      InputNode isbn = node.getNext("isbn");
      reference = isbn.getReference();
      prefix = isbn.getPrefix();
      
      assertTrue(isEmpty(prefix));
      assertEquals(reference, "http://www.default.com/");
   }
   
   private boolean isEmpty(String name) {
      return name == null || name.equals("");
   }
}
