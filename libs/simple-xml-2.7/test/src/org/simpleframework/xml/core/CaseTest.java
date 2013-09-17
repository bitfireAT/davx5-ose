package org.simpleframework.xml.core;

import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;

public class CaseTest extends ValidationTestCase {   
   
   private static final String SOURCE = 
   "<?xml version=\"1.0\"?>\n"+
   "<Example Version='1.0' Name='example' URL='http://domain.com/'>\n"+
   "   <List>\n"+   
   "      <ListEntry id='1'>\n"+
   "         <Text>one</Text>  \n\r"+
   "      </ListEntry>\n\r"+
   "      <ListEntry id='2'>\n"+
   "         <Text>two</Text>  \n\r"+
   "      </ListEntry>\n"+
   "      <ListEntry id='3'>\n"+
   "         <Text>three</Text>  \n\r"+
   "      </ListEntry>\n"+
   "   </List>\n"+
   "   <TextEntry id='4'>\n" +
   "      <Text>example 4</Text>\n" +
   "   </TextEntry>\n" +
   "   <URLList>\n"+
   "     <URLEntry>http://a.com/</URLEntry>\n"+
   "     <URLEntry>http://b.com/</URLEntry>\n"+
   "     <URLEntry>http://c.com/</URLEntry>\n"+
   "   </URLList>\n"+ 
   "   <TextEntry id='5'>\n" +
   "      <Text>example 5</Text>\n" +
   "   </TextEntry>\n" +
   "   <TextEntry id='6'>\n" +
   "      <Text>example 6</Text>\n" +
   "   </TextEntry>\n" +
   "</Example>";
   
   @Root(name="Example")
   private static class CaseExample {
      
      @ElementList(name="List", entry="ListEntry")
      private List<TextEntry> list;
      
      @ElementList(name="URLList")
      private List<URLEntry> domainList;
      
      @ElementList(name="TextList", inline=true)
      private List<TextEntry> textList;
      
      @Attribute(name="Version")
      private float version;
      
      @Attribute(name="Name")
      private String name;   
      
      @Attribute
      private String URL; // Java Bean property is URL
   }
   
   @Root(name="TextEntry")
   private static class TextEntry {
      
      @Attribute(name="id")
      private int id;
      
      @Element(name="Text")
      private String text;
   }
   
   @Root(name="URLEntry")
   private static class URLEntry {
      
      @Text
      private String location;
   }

   public void testCase() throws Exception {
      Persister persister = new Persister();
      CaseExample example = persister.read(CaseExample.class, SOURCE);
      
      assertEquals(example.version, 1.0f);
      assertEquals(example.name, "example");
      assertEquals(example.URL, "http://domain.com/");
      
      validate(example, persister);
   }
}
