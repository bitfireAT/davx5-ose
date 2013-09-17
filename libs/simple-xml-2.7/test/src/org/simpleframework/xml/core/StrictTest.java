package org.simpleframework.xml.core;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;

public class StrictTest extends ValidationTestCase {
        
   private static final String SOURCE =
    "<root version='2.1' id='234'>\n" +
    "   <list length='3' type='sorted'>\n" +
    "      <item name='1'>\n" +
    "         <value>value 1</value>\n" +
    "      </item>\n" +
    "      <item name='2'>\n" +
    "         <value>value 2</value>\n" +
    "      </item>\n" +
    "      <item name='3'>\n" +
    "         <value>value 3</value>\n" +
    "      </item>\n" +
    "   </list>\n" +
    "   <object name='name'>\n" +
    "      <integer>123</integer>\n" +
    "      <object name='key'>\n" +
    "         <integer>12345</integer>\n" +
    "      </object>\n" +
    "   </object>\n" +
    "</root>";

   private static final String SIMPLE =
    "<object name='name'>\n" +
    "   <integer>123</integer>\n" +
    "   <object name='key'>\n" +
    "      <integer>12345</integer>\n" +
    "   </object>\n" +
    "   <name>test</name>\n"+
    "</object>\n";
   
   private static final String SIMPLE_MISSING_NAME =
   "<object name='name'>\n" +
   "   <integer>123</integer>\n" +
   "   <object name='key'>\n" +
   "      <integer>12345</integer>\n" +
   "   </object>\n" +
   "</object>\n";
   
   @Root(name="root", strict=false)
   private static class StrictExample {

      @ElementArray(name="list", entry="item")
      private StrictEntry[] list;

      @Element(name="object")
      private StrictObject object;
   }

   @Root(name="entry", strict=false)
   private static class StrictEntry {

      @Element(name="value")
      private String value;
   }

   @Root(strict=false)
   private static class StrictObject {
   
      @Element(name="integer")
      private int integer;
   }  

   @Root(name="object", strict=false)
   private static class NamedStrictObject extends StrictObject {
      
      @Element(name="name")
      private String name;
   }

   private Persister persister;

   public void setUp() throws Exception {
      persister = new Persister();
   }
	
   public void testStrict() throws Exception {    
      StrictExample example = persister.read(StrictExample.class, SOURCE);

      assertEquals(example.list.length, 3);
      assertEquals(example.list[0].value, "value 1");   
      assertEquals(example.list[1].value, "value 2");     
      assertEquals(example.list[2].value, "value 3");     
      assertEquals(example.object.integer, 123);
      
      validate(example, persister);
   }

   //public void testUnnamedStrict() throws Exception {    
   //   boolean success = false; 
   //   
   //   try {      
   //      persister.read(StrictObject.class, SIMPLE);
   //   } catch(RootException e) {
   //      success = true;              
   //   }
   //   assertTrue(success);
   //}

   public void testNamedStrict() throws Exception {    
      StrictObject object = persister.read(NamedStrictObject.class, SIMPLE);

      assertEquals(object.integer, 123);
      validate(object, persister);
   }  
   
   public void testNamedStrictMissingName() throws Exception {    
      boolean failure = false;
      try {
         StrictObject object = persister.read(NamedStrictObject.class, SIMPLE_MISSING_NAME);
         assertNotNull(object);
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Did not fail", failure);
   } 
}
