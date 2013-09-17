package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;

import org.simpleframework.xml.ValidationTestCase;

public class TypeTest extends ValidationTestCase {
        
   private static final String SOURCE =
   "<?xml version=\"1.0\"?>\n"+
   "<test>\n"+
   "   <primitive>\n"+
   "     <boolean>true</boolean>\r\n"+
   "     <byte>16</byte>  \n\r"+
   "     <short>120</short>  \n\r"+
   "     <int>1234</int>\n"+
   "     <float>1234.56</float>  \n\r"+
   "     <long>1234567</long>\n"+
   "     <double>1234567.89</double>  \n\r"+
   "   </primitive>\n"+
   "   <object>\n"+
   "     <Boolean>true</Boolean>\r\n"+
   "     <Byte>16</Byte>  \n\r"+
   "     <Short>120</Short>  \n\r"+
   "     <Integer>1234</Integer>\n"+
   "     <Float>1234.56</Float>  \n\r"+
   "     <Long>1234567</Long>\n"+
   "     <Double>1234567.89</Double>  \n\r"+
   "     <String>text value</String>\n\r"+
   "     <Enum>TWO</Enum>\n"+
   "   </object>\n\r"+
   "</test>";
   
   @Root(name="test")
   private static class Entry {

      @Element(name="primitive")
      private PrimitiveEntry primitive;

      @Element(name="object")
      private ObjectEntry object;
   }

   private static class PrimitiveEntry {

      @Element(name="boolean")
      private boolean booleanValue;            

      @Element(name="byte")
      private byte byteValue;

      @Element(name="short")
      private short shortValue;

      @Element(name="int")
      private int intValue;   

      @Element(name="float")
      private float floatValue;

      @Element(name="long")
      private long longValue;         

      @Element(name="double")
      private double doubleValue;
   }

   private static class ObjectEntry {

      @Element(name="Boolean")
      private Boolean booleanValue;              

      @Element(name="Byte")
      private Byte byteValue;

      @Element(name="Short")
      private Short shortValue;

      @Element(name="Integer")
      private Integer intValue;   

      @Element(name="Float")
      private Float floatValue;

      @Element(name="Long")
      private Long longValue;         

      @Element(name="Double")
      private Double doubleValue;

      @Element(name="String")
      private String stringValue;

      @Element(name="Enum")
      private TestEnum enumValue;              
   }  

   private static enum TestEnum { 

      ONE,
      TWO,
      THREE
   }

   private Persister persister;

   public void setUp() throws Exception {
      persister = new Persister();
   }
	
   public void testPrimitive() throws Exception {    
      Entry entry = persister.read(Entry.class, SOURCE);
      entry = persister.read(Entry.class, SOURCE);
     
      assertEquals(entry.primitive.booleanValue, true);
      assertEquals(entry.primitive.byteValue, 16);
      assertEquals(entry.primitive.shortValue, 120);
      assertEquals(entry.primitive.intValue, 1234);
      assertEquals(entry.primitive.floatValue, 1234.56f);
      assertEquals(entry.primitive.longValue, 1234567l);
      assertEquals(entry.primitive.doubleValue, 1234567.89d);

      assertEquals(entry.object.booleanValue, Boolean.TRUE);
      assertEquals(entry.object.byteValue, new Byte("16"));
      assertEquals(entry.object.shortValue, new Short("120"));
      assertEquals(entry.object.intValue, new Integer(1234));
      assertEquals(entry.object.floatValue, new Float(1234.56));
      assertEquals(entry.object.longValue, new Long(1234567));
      assertEquals(entry.object.doubleValue, new Double(1234567.89));
      assertEquals(entry.object.stringValue, "text value");
      assertEquals(entry.object.enumValue, TestEnum.TWO);
     
      StringWriter buffer = new StringWriter();
      persister.write(entry, buffer);
      validate(entry, persister);

      entry = persister.read(Entry.class, buffer.toString());

      assertEquals(entry.primitive.booleanValue, true);
      assertEquals(entry.primitive.byteValue, 16);
      assertEquals(entry.primitive.shortValue, 120);
      assertEquals(entry.primitive.intValue, 1234);
      assertEquals(entry.primitive.floatValue, 1234.56f);
      assertEquals(entry.primitive.longValue, 1234567l);
      assertEquals(entry.primitive.doubleValue, 1234567.89d);

      assertEquals(entry.object.booleanValue, Boolean.TRUE);      
      assertEquals(entry.object.byteValue, new Byte("16"));
      assertEquals(entry.object.shortValue, new Short("120"));
      assertEquals(entry.object.intValue, new Integer(1234));
      assertEquals(entry.object.floatValue, new Float(1234.56));
      assertEquals(entry.object.longValue, new Long(1234567));
      assertEquals(entry.object.doubleValue, new Double(1234567.89));
      assertEquals(entry.object.stringValue, "text value");
      assertEquals(entry.object.enumValue, TestEnum.TWO);         
      
      validate(entry, persister);
   }
}
