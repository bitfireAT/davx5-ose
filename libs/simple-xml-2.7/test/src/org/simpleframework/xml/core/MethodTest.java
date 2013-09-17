package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;

import org.simpleframework.xml.ValidationTestCase;

public class MethodTest extends ValidationTestCase {
        
   private static final String SOURCE_EXPLICIT =
   "<?xml version=\"1.0\"?>\n"+
   "<test>\n"+
   "   <boolean>true</boolean>\r\n"+
   "   <byte>16</byte>  \n\r"+
   "   <short>120</short>  \n\r"+
   "   <int>1234</int>\n"+
   "   <float>1234.56</float>  \n\r"+
   "   <long>1234567</long>\n"+
   "   <double>1234567.89</double>  \n\r"+
   "</test>";      

   private static final String SOURCE_IMPLICIT =
   "<?xml version=\"1.0\"?>\n"+
   "<implicitMethodNameExample>\n"+
   "   <booleanValue>true</booleanValue>\r\n"+
   "   <byteValue>16</byteValue>  \n\r"+
   "   <shortValue>120</shortValue>  \n\r"+
   "   <intValue>1234</intValue>\n"+
   "   <floatValue>1234.56</floatValue>  \n\r"+
   "   <longValue>1234567</longValue>\n"+
   "   <doubleValue>1234567.89</doubleValue>  \n\r"+
   "</implicitMethodNameExample>"; 

   @Root(name="test")
   private static class ExplicitMethodNameExample {

      protected boolean booleanValue;            
      protected byte byteValue;
      protected short shortValue;
      protected int intValue;   
      protected float floatValue;
      protected long longValue;         
      protected double doubleValue;
      
      public ExplicitMethodNameExample() {
         super();
      }
      
      @Element(name="boolean")
      public boolean getBooleanValue() {
         return booleanValue;
      }
      
      @Element(name="boolean")
      public void setBooleanValue(boolean booleanValue) {
         this.booleanValue = booleanValue;
      }
      
      @Element(name="byte")
      public byte getByteValue() {
         return byteValue;
      }

      @Element(name="byte")
      public void setByteValue(byte byteValue) {
         this.byteValue = byteValue;
      }
      
      @Element(name="double")
      public double getDoubleValue() {
         return doubleValue;
      }
      
      @Element(name="double")
      public void setDoubleValue(double doubleValue) {
         this.doubleValue = doubleValue;
      }
      
      @Element(name="float")
      public float getFloatValue() {
         return floatValue;
      }
      
      @Element(name="float")
      public void setFloatValue(float floatValue) {
         this.floatValue = floatValue;
      }
      
      @Element(name="int")
      public int getIntValue() {
         return intValue;
      }
      
      @Element(name="int")
      public void setIntValue(int intValue) {
         this.intValue = intValue;
      }
      
      @Element(name="long")
      public long getLongValue() {
         return longValue;
      }
      
      @Element(name="long")
      public void setLongValue(long longValue) {
         this.longValue = longValue;
      }
      
      @Element(name="short")
      public short getShortValue() {
         return shortValue;
      }
      
      @Element(name="short")
      public void setShortValue(short shortValue) {
         this.shortValue = shortValue;
      }           
   }

   @Root
   private static class ImplicitMethodNameExample extends ExplicitMethodNameExample {

      @Element
      public boolean getBooleanValue() {
         return booleanValue;
      }
      
      @Element
      public void setBooleanValue(boolean booleanValue) {
         this.booleanValue = booleanValue;
      }
      
      @Element
      public byte getByteValue() {
         return byteValue;
      }

      @Element
      public void setByteValue(byte byteValue) {
         this.byteValue = byteValue;
      }
      
      @Element
      public double getDoubleValue() {
         return doubleValue;
      }
      
      @Element
      public void setDoubleValue(double doubleValue) {
         this.doubleValue = doubleValue;
      }
      
      @Element
      public float getFloatValue() {
         return floatValue;
      }
      
      @Element
      public void setFloatValue(float floatValue) {
         this.floatValue = floatValue;
      }
      
      @Element
      public int getIntValue() {
         return intValue;
      }
      
      @Element
      public void setIntValue(int intValue) {
         this.intValue = intValue;
      }
      
      @Element
      public long getLongValue() {
         return longValue;
      }
      
      @Element
      public void setLongValue(long longValue) {
         this.longValue = longValue;
      }
      
      @Element
      public short getShortValue() {
         return shortValue;
      }
      
      @Element
      public void setShortValue(short shortValue) {
         this.shortValue = shortValue;
      }           
   }

   private Persister persister;

   public void setUp() throws Exception {
      persister = new Persister();
   }
	
   public void testExplicitMethodNameExample() throws Exception {    
      ExplicitMethodNameExample entry = persister.read(ExplicitMethodNameExample.class, SOURCE_EXPLICIT);

      assertEquals(entry.booleanValue, true);
      assertEquals(entry.byteValue, 16);
      assertEquals(entry.shortValue, 120);
      assertEquals(entry.intValue, 1234);
      assertEquals(entry.floatValue, 1234.56f);
      assertEquals(entry.longValue, 1234567l);
      assertEquals(entry.doubleValue, 1234567.89d);
     
      StringWriter buffer = new StringWriter();
      persister.write(entry, buffer);
      validate(entry, persister);

      entry = persister.read(ExplicitMethodNameExample.class, buffer.toString());

      assertEquals(entry.booleanValue, true);
      assertEquals(entry.byteValue, 16);
      assertEquals(entry.shortValue, 120);
      assertEquals(entry.intValue, 1234);
      assertEquals(entry.floatValue, 1234.56f);
      assertEquals(entry.longValue, 1234567l);
      assertEquals(entry.doubleValue, 1234567.89d);
      
      validate(entry, persister);
   }
   	
   public void testImplicitMethodNameExample() throws Exception {   
      ImplicitMethodNameExample entry = persister.read(ImplicitMethodNameExample.class, SOURCE_IMPLICIT);
   
      assertEquals(entry.booleanValue, true);
      assertEquals(entry.byteValue, 16);
      assertEquals(entry.shortValue, 120);
      assertEquals(entry.intValue, 1234);
      assertEquals(entry.floatValue, 1234.56f);
      assertEquals(entry.longValue, 1234567l);
      assertEquals(entry.doubleValue, 1234567.89d);
     
      StringWriter buffer = new StringWriter();
      persister.write(entry, buffer);
      validate(entry, persister);
   
      entry = persister.read(ImplicitMethodNameExample.class, buffer.toString());
   
      assertEquals(entry.booleanValue, true);
      assertEquals(entry.byteValue, 16);
      assertEquals(entry.shortValue, 120);
      assertEquals(entry.intValue, 1234);
      assertEquals(entry.floatValue, 1234.56f);
      assertEquals(entry.longValue, 1234567l);
      assertEquals(entry.doubleValue, 1234567.89d);
      
      validate(entry, persister);
   }


}
