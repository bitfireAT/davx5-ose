package org.simpleframework.xml.core;

import java.io.StringReader;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;

import junit.framework.TestCase;

public class ExtendTest extends TestCase {

   private static final String FIRST =
   "<?xml version=\"1.0\"?>\n"+
   "<root id='12'>\n"+
   "   <text>entry text</text>  \n\r"+
   "</root>";
   
   private static final String SECOND =
   "<?xml version=\"1.0\"?>\n"+
   "<root id='12'>\n"+
   "   <text>entry text</text>  \n\r"+
   "   <name>some name</name> \n\r"+
   "</root>";

   private static final String THIRD =
   "<?xml version=\"1.0\"?>\n"+
   "<override id='12' flag='true'>\n"+
   "   <text>entry text</text>  \n\r"+
   "   <name>some name</name> \n"+
   "   <third>added to schema</third>\n"+
   "</override>";

   @Root(name="root")
   private static class First {

      @Attribute(name="id")           
      public int id;           
           
      @Element(name="text")
      public String text;           
   }

   private static class Second extends First {

      @Element(name="name")
      public String name;
   }

   @Root(name="override")
   private static class Third extends Second {

      @Attribute(name="flag")
      public boolean bool;              
           
      @Element(name="third")
      public String third;             
   }
        
	private Persister serializer;

	public void setUp() {
	   serializer = new Persister();
	}
	
   public void testFirst() throws Exception {    
      First first = serializer.read(First.class, new StringReader(FIRST));
      
      assertEquals(first.id, 12);
      assertEquals(first.text, "entry text");
   }
   
   public void testSecond() throws Exception {
      Second second = serializer.read(Second.class, new StringReader(SECOND));
      
      assertEquals(second.id, 12);
      assertEquals(second.text, "entry text");
      assertEquals(second.name, "some name");
   }

   public void testThird() throws Exception {
      Third third = serializer.read(Third.class, new StringReader(THIRD));

      assertEquals(third.id, 12);
      assertEquals(third.text, "entry text");
      assertEquals(third.name, "some name");
      assertEquals(third.third, "added to schema");   
      assertTrue(third.bool);
   }

   public void testFailure() throws Exception {
      boolean fail = false;

      try {
         Third third = serializer.read(Third.class, new StringReader(SECOND));
      }catch(Exception e) {
         fail = true;              
      }
      assertTrue(fail);      
   }
}
