package org.simpleframework.xml.core;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.core.Commit;
import org.simpleframework.xml.core.Persister;

import junit.framework.TestCase;

public class InjectionTest extends TestCase {

   private static final String SOURCE =
   "<?xml version=\"1.0\"?>\n"+
   "<injectionExample id='12' flag='true'>\n"+
   "   <text>entry text</text>  \n\r"+
   "   <date>01/10/1916</date> \n"+
   "   <message trim='true'>\r\n"+
   "        This is an example message.\r\n"+
   "   </message>\r\n"+
   "</injectionExample>";

   @Root
   private static class InjectionExample {

      @Attribute
      private boolean flag;              

      @Attribute
      private int id;              
           
      @Element
      private String text;

      @Element
      private String date;
      
      @Element
      private ExampleMessage message;

      private String name;

      public InjectionExample(String name) {
         this.name = name;              
      }
   }
   
   private static class ExampleMessage {
    
      @Attribute
      private boolean trim;
      
      @Text
      private String text;
      
      @Commit
      private void prepare() {
         if(trim) {
            text = text.trim();
         }
      }
   }
        
	private Persister serializer;

	public void setUp() {
	   serializer = new Persister();
	}
	
   public void testFirst() throws Exception {    
      InjectionExample example = new InjectionExample("name");  
          
      assertEquals(example.flag, false);
      assertEquals(example.id, 0);
      assertEquals(example.text, null);
      assertEquals(example.date, null);
      assertEquals(example.name, "name");
      assertEquals(example.message, null);
      
      InjectionExample result = serializer.read(example, SOURCE);
      
      assertEquals(example, result);      
      assertEquals(example.flag, true);
      assertEquals(example.id, 12);
      assertEquals(example.text, "entry text");
      assertEquals(example.date, "01/10/1916");
      assertEquals(example.name, "name");  
      assertEquals(example.message.trim, true);
      assertEquals(example.message.text, "This is an example message.");
   }
}
