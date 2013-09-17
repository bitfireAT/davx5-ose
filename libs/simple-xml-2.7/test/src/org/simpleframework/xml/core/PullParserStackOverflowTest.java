package org.simpleframework.xml.core;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.ValidationTestCase;

public class PullParserStackOverflowTest extends ValidationTestCase {

   private static final String BROKEN_ROOT_INCOMPLETE = 
   "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
   "level/>"; 
   
   private static final String BROKEN_ROOT_COMPLETE = 
   "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
   "level number='10'><text>example text</text></level>"; 
   
   private static final String BROKEN_ELEMENT_COMPLETE = 
   "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
   "<level number='10'>text>example text</text></level>"; 
   
   @Root(name = "level")
   public static class Level {
      @Attribute(name="number")
      private int levelNumber;
      @Element(name="text")
      private String name;
   }
   
   public void testBrokenStart() throws Exception {
      Serializer serializer = new Persister();
      byte[] data = BROKEN_ROOT_INCOMPLETE.getBytes("ISO-8859-1");
      InputStream stream = new ByteArrayInputStream(data);
      boolean exception = false;
      try {
         serializer.read(Level.class, stream);
      }catch(Exception e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Exception should have been thrown for broken root", exception);
   }
   
   public void testBrokenStartButComplete() throws Exception {
      Serializer serializer = new Persister();
      byte[] data = BROKEN_ROOT_COMPLETE.getBytes("ISO-8859-1");
      InputStream stream = new ByteArrayInputStream(data);
      boolean exception = false;
      try {
         serializer.read(Level.class, stream);
      }catch(Exception e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Exception should have been thrown for broken root", exception);
   }
   
   public void testBrokenElementButComplete() throws Exception {
      Serializer serializer = new Persister();
      byte[] data = BROKEN_ELEMENT_COMPLETE.getBytes("ISO-8859-1");
      InputStream stream = new ByteArrayInputStream(data);
      boolean exception = false;
      try {
         serializer.read(Level.class, stream);
      }catch(Exception e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Exception should have been thrown for broken element", exception);
   }
}
