package org.simpleframework.xml.core;

import java.util.List;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.stream.Format;

public class HtmlParseTest extends ValidationTestCase {
   
   private static final String HTML =
   "<html>\n"+
   "<body>\n"+
   "<p>\n"+
   "This is a test <b>Bold text</b> other text. This is <i>italics</i> and"+
   " also another <p>Paragraph <i>italic</i> inside a paragraph</p>"+
   "</p>\n"+
   "</body>\n"+
   "</html>\n";
   
   @Root
   private static class Bold {
      
      @Text
      private String text;
   }
   
   @Root
   private static class Italic {
      
      @Text
      private String text;
   }
   
   @Root
   private static class Paragraph {
      
      @Text
      @ElementListUnion({
         @ElementList(entry="b", type=Bold.class, required=false, inline=true),
         @ElementList(entry="i", type=Italic.class, required=false, inline=true),
         @ElementList(entry="p", type=Paragraph.class, required=false, inline=true),
      })
      private List<Object> boldOrText;
   }
   
   @Root
   private static class Body {
      
      @ElementList(entry="p", inline=true)
      private List<Paragraph> list;
   }
   
   
   @Root
   private static class Document {
      
      @Element
      private Body body;
   }

   public void testDocument() throws Exception {
      Format format = new Format(0);
      Persister persister = new Persister(format);
      Document doc = persister.read(Document.class, HTML);
      
      assertNotNull(doc.body);
      
      Body body = doc.body;
      List<Paragraph> paragraphs = body.list;
      
      assertEquals(paragraphs.size(), 1);
      assertEquals(paragraphs.get(0).boldOrText.size(), 6);
      assertEquals(paragraphs.get(0).boldOrText.get(0), "\nThis is a test ");
      assertEquals(paragraphs.get(0).boldOrText.get(1).getClass(), Bold.class);
      assertEquals(paragraphs.get(0).boldOrText.get(2), " other text. This is ");
      assertEquals(paragraphs.get(0).boldOrText.get(3).getClass(), Italic.class);
      assertEquals(paragraphs.get(0).boldOrText.get(4), " and also another ");
      assertEquals(paragraphs.get(0).boldOrText.get(5).getClass(), Paragraph.class);
      
      validate(persister, doc);
   }
}
