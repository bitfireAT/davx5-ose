package org.simpleframework.xml.core;

import java.util.List;
import java.util.Map;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.HyphenStyle;
import org.simpleframework.xml.stream.Style;

public class StyleTest extends ValidationTestCase {   
   
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
   "   <TextArray length='3'>\n"+
   "      <TextEntry id='6'>\n" +
   "         <Text>example 6</Text>\n" +
   "      </TextEntry>\n" +
   "      <TextEntry id='7'>\n" +
   "         <Text>example 7</Text>\n" +
   "      </TextEntry>\n" +
   "      <TextEntry id='8'>\n" +
   "         <Text>example 8</Text>\n" +
   "      </TextEntry>\n" +
   "   </TextArray>\n"+
   "   <TextEntry id='9'>\n" +
   "      <Text>example 9</Text>\n" +
   "   </TextEntry>\n" +
   "   <URLMap>\n"+
   "     <URLItem Key='a'>\n"+
   "        <URLEntry>http://a.com/</URLEntry>\n"+
   "     </URLItem>\n"+
   "     <URLItem Key='b'>\n"+
   "        <URLEntry>http://b.com/</URLEntry>\n"+
   "     </URLItem>\n"+
   "     <URLItem Key='c'>\n"+
   "        <URLEntry>http://c.com/</URLEntry>\n"+
   "     </URLItem>\n"+
   "   </URLMap>\n"+ 
   "</Example>";
   
   @Root(name="Example")
   private static class CaseExample {
      
      @ElementList(name="List", entry="ListEntry")
      private List<TextEntry> list;
      
      @ElementList(name="URLList")
      private List<URLEntry> domainList;
      
      @ElementList(name="TextList", inline=true)
      private List<TextEntry> textList;
      
      @ElementArray(name="TextArray", entry="TextEntry")
      private TextEntry[] textArray;
      
      @ElementMap(name="URLMap", entry="URLItem", key="Key", value="URLItem", attribute=true)
      private Map<String, URLEntry> domainMap;
      
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
      Style style = new HyphenStyle();
      Format format = new Format(style);
      Persister writer = new Persister(format);
      Persister reader = new Persister();
      CaseExample example = reader.read(CaseExample.class, SOURCE);
      
      assertEquals(example.version, 1.0f);
      assertEquals(example.name, "example");
      assertEquals(example.URL, "http://domain.com/");
      
      writer.write(example, System.err);
      validate(example, reader); 
      validate(example, writer);
   }
}
