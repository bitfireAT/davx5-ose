package org.simpleframework.xml.core;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;

public class TutorialTest extends TestCase {
   
   private static final String SOURCE =
   "<document title='Secret Document' xmlns='http://www.domain.com/document'>\n"+
   "   <user:author xmlns:user='http://www.domain.com/user'>Niall Gallagher</user:author>\n"+
   "   <contact>niallg@users.sourceforge.net</contact>\n"+
   "   <detail xmlns='http://www.domain.com/detail'>\n"+
   "      <publisher>Stanford Press</publisher>\n"+
   "      <date>2001</date>\n"+
   "      <address>Palo Alto</address>\n"+
   "      <edition>1st</edition>\n"+
   "      <ISBN>0-69-697269-4</ISBN>\n"+
   "   </detail>\n"+
   "   <section name='Introduction' xmlns:para='http://www.domain.com/paragraph'>\n"+
   "      <para:paragraph>First paragraph of document</para:paragraph>\n"+
   "      <para:paragraph>Second paragraph in the document</para:paragraph>\n"+
   "      <para:paragraph>Third and final paragraph</para:paragraph>\n"+
   "   </section>\n"+
   "</document>\n";
     
   @Root
   @Namespace(reference = "http://www.domain.com/document")
   public static class Document {
      @Element(name="author")
      @Namespace(prefix="user", reference="http://www.domain.com/user")
      private static final String AUTHOR = "Niall Gallagher";
      @Element(name="contact")
      private static final String CONTACT = "niallg@users.sourceforge.net";
      @Element(name="detail")
      private static final Detail DETAIL = new Detail(
         "Stanford Press",
         "2001",
         "Palo Alto",
         "1st",
         "0-69-697269-4");

      @ElementList(inline=true)
      private List<Section> list;
      @Attribute
      private String title;
      private Document() {
         super();
      }
      public Document(String title) {
         this.list = new ArrayList<Section>();
         this.title = title;
      }
      public void add(Section section) {
         list.add(section);
      }
   }
   @Root
   @Namespace(reference="http://www.domain.com/detail")
   public static class Detail {
      @Element
      private String publisher;
      @Element
      private String date;
      @Element
      private String address;
      @Element
      private String edition;
      @Element
      private String ISBN;
      private Detail() {
         super();
      }     
      public Detail(String publisher, String date, String address, String edition, String ISBN) {
         this.publisher = publisher;
         this.address = address;
         this.edition = edition;
         this.date = date;
         this.ISBN = ISBN;
      }
   }
   @Root
   @NamespaceList({
   @Namespace(prefix="para", reference="http://www.domain.com/paragraph")})
   public static class Section {
      @Attribute
      private String name;
      @ElementList(inline = true)
      private List<Paragraph> list;
      private Section() {
         super();
      }
      public Section(String name) {
         this.list = new ArrayList<Paragraph>();
         this.name = name;
      }
      public void add(Paragraph paragraph) {
         list.add(paragraph);
      }
   }
   @Root
   @Namespace(reference = "http://www.domain.com/paragraph")
   public static class Paragraph {
      private String text;
      @Text
      private String getContent() {
         return text;
      }
      @Text
      public void setContent(String text) {
         this.text = text;
      }
   }

   public void testTutorial() throws Exception {
      Persister persister = new Persister();
      Document document = persister.read(Document.class, SOURCE);
      persister.write(document,System.out);
   }
}
