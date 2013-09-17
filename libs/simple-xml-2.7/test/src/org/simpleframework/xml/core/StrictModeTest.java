package org.simpleframework.xml.core;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class StrictModeTest extends ValidationTestCase {

   private static final String SOURCE =
   "<object name='name' version='1.0'>\n" +
   "   <integer>123</integer>\n" +
   "   <object name='key'>\n" +
   "      <integer>12345</integer>\n" +
   "   </object>\n" +
   "   <address type='full'>\n"+
   "      <name>example name</name>\n"+
   "      <address>example address</address>\n"+
   "      <city>example city</city>\n"+
   "   </address>\n"+
   "   <name>test</name>\n"+
   "</object>\n";
  
   private static final String SOURCE_MISSING_NAME =
   "<object name='name' version='1.0'>\n" +
   "   <integer>123</integer>\n" +
   "   <object name='key'>\n" +
   "      <integer>12345</integer>\n" +
   "   </object>\n" +
   "   <address type='full'>\n"+
   "      <name>example name</name>\n"+
   "      <address>example address</address>\n"+
   "      <city>example city</city>\n"+
   "   </address>\n"+
   "</object>\n";
      
   @Root
   private static class ExampleObject {
      @Element int integer;
      @Attribute double version;
   }  
   
   @Root
   private static class Address {
      @Element String name;
      @Element String address;
   }
   
   @Root
   private static class ExampleObjectWithAddress extends ExampleObject {
      @Element Address address;
      @Element String name;
   }
     
   public void testStrictMode() throws Exception {
      boolean failure = false;
      
      try {
         Persister persister = new Persister();
         ExampleObjectWithAddress object = persister.read(ExampleObjectWithAddress.class, SOURCE);
         assertNull(object);
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Serialzed correctly", failure);
   }
   
   public void testNonStrictMode() throws Exception {
      Persister persister = new Persister();
      ExampleObjectWithAddress object = persister.read(ExampleObjectWithAddress.class, SOURCE, false);
      
      assertEquals(object.version, 1.0);
      assertEquals(object.integer, 123);
      assertEquals(object.address.name, "example name");
      assertEquals(object.address.address, "example address");
      assertEquals(object.name, "test");
      
      validate(object, persister);
   }  
   
   public void testNonStrictModeMissingName() throws Exception {
      boolean failure = false;
      
      try {
         Persister persister = new Persister();
         ExampleObjectWithAddress object = persister.read(ExampleObjectWithAddress.class, SOURCE_MISSING_NAME, false);
         assertNull(object);
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Serialzed correctly", failure);
   } 
   
   public void testValidation() throws Exception {
      Persister persister = new Persister();
      
      assertTrue(persister.validate(ExampleObjectWithAddress.class, SOURCE, false));
      
      try {
         assertFalse(persister.validate(ExampleObjectWithAddress.class, SOURCE));  
         assertFalse(true);
      }catch(Exception e){
         e.printStackTrace();
      }
      try {
         assertFalse(persister.validate(ExampleObjectWithAddress.class, SOURCE_MISSING_NAME)); 
         assertFalse(true);
      }catch(Exception e){
         e.printStackTrace();
      }
      try {
         assertFalse(persister.validate(ExampleObjectWithAddress.class, SOURCE_MISSING_NAME, false)); 
         assertFalse(true);
      }catch(Exception e){
         e.printStackTrace();
      }
   }
}
