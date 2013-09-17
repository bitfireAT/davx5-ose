package org.simpleframework.xml.core;

import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;

public class StaticTest extends ValidationTestCase {
   
   private static final String SOURCE =   
   "<document title='Secret Document' xmlns='http://www.domain.com/document'>\n"+
   "   <author>Niall Gallagher</author>\n"+
   "   <contact>niallg@users.sourceforge.net</contact>\n"+
   "   <detail xmlns='http://www.domain.com/detail'>\n"+
   "      <publisher>Stanford Press</publisher>\n"+
   "      <date>2001</date>\n"+
   "      <address>Palo Alto</address>\n"+
   "      <edition>1st</edition>\n"+
   "      <ISBN>0-69-697269-4</ISBN>\n"+
   "   </detail>\n"+
   "   <section name='Introduction' xmlns='http://www.domain.com/section'>\n"+
   "      <paragraph xmlns='http://www.domain.com/paragraph'>First paragraph of document</paragraph>\n"+
   "      <paragraph xmlns='http://www.domain.com/paragraph'>Second paragraph in the document</paragraph>\n"+
   "      <paragraph xmlns='http://www.domain.com/paragraph'>Third and final paragraph</paragraph>\n"+
   "   </section>\n"+
   "</document>";

   @Root
   @Namespace(reference="http://www.domain.com/detail")
   private static class Detail {
      
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
   @Namespace(reference = "http://www.domain.com/document")
   public static class Document {

      @Element(name="author")
      @Namespace(prefix="user", reference="http://www.domain.com/user")
      private static String AUTHOR = "Niall Gallagher";

      @Element(name="contact")
      private static String CONTACT = "niallg@users.sourceforge.net";
      
      @Element(name="detail")
      private static Detail DETAIL = new Detail(
         "Stanford Press",
         "2001",
         "Palo Alto",
         "1st",
         "0-69-697269-4");

      @ElementList(inline = true)
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
   @NamespaceList({
      @Namespace(prefix="para", reference="http://www.domain.com/paragraph")
   })
   private static class Section {

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
   private static class Paragraph {

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

   public void testStatic() throws Exception {
      Persister persister = new Persister();
      Document document = new Document("Secret Document");
      Section section = new Section("Introduction");
      Paragraph first = new Paragraph();
      Paragraph second = new Paragraph();
      Paragraph third = new Paragraph();

      first.setContent("First paragraph of document");
      second.setContent("Second paragraph in the document");
      third.setContent("Third and final paragraph");
      section.add(first);
      section.add(second);
      section.add(third);
      document.add(section);

      persister.write(document, System.out);
      
      validate(persister, document);
   }
}
