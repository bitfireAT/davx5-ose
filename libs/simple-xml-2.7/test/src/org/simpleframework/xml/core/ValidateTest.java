package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;

public class ValidateTest extends ValidationTestCase {
   
   private static final String VERSION_MISSING =
   "<test>\n"+
   "   <array length='3'>\n"+
   "     <entry name='a' version='ONE'>Example 1</entry>\r\n"+
   "     <entry name='b' version='TWO'>Example 2</entry>\r\n"+
   "     <entry name='c'>Example 3</entry>\r\n"+
   "   </array>\n\r"+
   "</test>";
   
   private static final String NAME_MISSING =
   "<test>\n"+
   "   <array length='3'>\n"+
   "     <entry version='ONE'>Example 1</entry>\r\n"+
   "     <entry name='b' version='TWO'>Example 2</entry>\r\n"+
   "     <entry name='c' version='THREE'>Example 3</entry>\r\n"+
   "   </array>\n\r"+
   "</test>";
   
   private static final String TEXT_MISSING =
   "<test>\n"+
   "   <array length='3'>\n"+
   "     <entry name='a' version='ONE'>Example 1</entry>\r\n"+
   "     <entry name='b' version='TWO'>Example 2</entry>\r\n"+
   "     <entry name='c' version='THREE'/>\r\n"+
   "   </array>\n\r"+
   "</test>";
   
   private static final String EXTRA_ELEMENT =
   "<test>\n"+
   "   <array length='3'>\n"+
   "     <entry name='a' version='ONE'>Example 1</entry>\r\n"+
   "     <entry name='b' version='TWO'>Example 2</entry>\r\n"+
   "     <entry name='c' version='THREE'>Example 3</entry>\r\n"+
   "   </array>\n\r"+
   "   <array length='4'>\n"+
   "     <entry name='f' version='ONE'>Example 4</entry>\r\n"+
   "   </array>\n"+
   "</test>";
   
   @Root(name="test")
   private static class TextList {

      @ElementArray(name="array", entry="entry")
      private TextEntry[] array;
   }

   @Root(name="text")
   private static class TextEntry {

      @Attribute(name="name")
      private String name;

      @Attribute(name="version")
      private Version version;        

      @Text(data=true)
      private String text;
   }
   
   private enum Version {
      
      ONE,
      TWO,
      THREE
   }
   
   public void testVersionMissing() throws Exception {
      Serializer persister = new Persister();
      boolean success = false;
      
      try {
         success = persister.validate(TextList.class, VERSION_MISSING);
      } catch(Exception e) {
         e.printStackTrace();
         success = true;
      }
      assertTrue(success);
   }
   
   public void testNameMissing() throws Exception {
      Serializer persister = new Persister();
      boolean success = false;
      
      try {
         success = persister.validate(TextList.class, NAME_MISSING);
      } catch(Exception e) {
         e.printStackTrace();
         success = true;
      }
      assertTrue(success);
   }
   
   public void testTextMissing() throws Exception {
      Serializer persister = new Persister();
      boolean success = false;
      
      try {
         success = persister.validate(TextList.class, TEXT_MISSING);
      } catch(Exception e) {
         e.printStackTrace();
         success = true;
      }
      assertTrue(success);
   }
   
   public void testExtraElement() throws Exception {
      Serializer persister = new Persister();
      boolean success = false;
      
      try {
         success = persister.validate(TextList.class, EXTRA_ELEMENT);
      } catch(Exception e) {
         e.printStackTrace();
         success = true;
      }
      assertTrue(success);
   }
}
