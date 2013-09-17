package org.simpleframework.xml.strategy;

import java.io.StringWriter;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.CycleStrategy;

import org.simpleframework.xml.ValidationTestCase;

public class PrimitiveCycleTest extends ValidationTestCase {
           
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
   private static class PrimitiveCycleEntry {

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
   
   @Root
   private static class StringReferenceExample {
      
      @Element
      private String a;
      
      @Element
      private String b;
      
      @Element
      private String c;
      
      public StringReferenceExample() {
         super();
      }
      
      public StringReferenceExample(String a, String b, String c) {
         this.a = a;
         this.b = b;
         this.c = c;         
      }        
   }
   
   @Root
   private static class IntegerReferenceExample {
      
      @Element
      private Integer a;
      
      @Element
      private Integer b;
      
      @Element
      private Integer c;
      
      public IntegerReferenceExample() {
         super();
      }
      
      public IntegerReferenceExample(Integer a, Integer b, Integer c) {
         this.a = a;
         this.b = b;
         this.c = c;         
      }        
   }
   
   private Persister persister;

   public void setUp() throws Exception {
      persister = new Persister(new CycleStrategy());
   }
	
   public void testPrimitive() throws Exception {    
      PrimitiveCycleEntry entry = persister.read(PrimitiveCycleEntry.class, SOURCE);

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

      StringWriter out = new StringWriter();
      persister.write(entry, out);
      String text = out.toString();

      assertElementHasAttribute(text, "/test", "id", "0");
      assertElementHasAttribute(text, "/test/primitive", "id", "1");
      assertElementHasAttribute(text, "/test/primitive/boolean", "id", "2");
      assertElementHasAttribute(text, "/test/primitive/byte", "id", "3");
      assertElementHasAttribute(text, "/test/primitive/short", "id", "4");
      assertElementHasAttribute(text, "/test/primitive/int", "id", "5");
      assertElementHasAttribute(text, "/test/primitive/float", "id", "6");
      assertElementHasAttribute(text, "/test/primitive/long", "id", "7");
      assertElementHasAttribute(text, "/test/primitive/double", "id", "8");     

      assertElementHasValue(text, "/test/primitive/boolean", "true");   
      assertElementHasValue(text, "/test/primitive/byte", "16");  
      assertElementHasValue(text, "/test/primitive/short", "120");       
      assertElementHasValue(text, "/test/primitive/int", "1234");        
      assertElementHasValue(text, "/test/primitive/float", "1234.56");       
      assertElementHasValue(text, "/test/primitive/long", "1234567");       
      assertElementHasValue(text, "/test/primitive/double", "1234567.89");
 
      assertElementHasValue(text, "/test/object/Boolean", "true");   
      assertElementHasValue(text, "/test/object/Byte", "16");  
      assertElementHasValue(text, "/test/object/Short", "120");       
      assertElementHasValue(text, "/test/object/Integer", "1234");        
      assertElementHasValue(text, "/test/object/Float", "1234.56");       
      assertElementHasValue(text, "/test/object/Long", "1234567");       
      assertElementHasValue(text, "/test/object/Double", "1234567.89");     
      assertElementHasValue(text, "/test/object/String", "text value");       
      assertElementHasValue(text, "/test/object/Enum", "TWO");  
      
      validate(entry, persister);
   }
   
   public void testPrimitiveReference() throws Exception {
      StringReferenceExample example = new StringReferenceExample("a", "a", "a");      
      StringWriter out = new StringWriter();
      persister.write(example, out);
      String text = out.toString();

      assertElementHasAttribute(text, "/stringReferenceExample", "id", "0");
      assertElementHasAttribute(text, "/stringReferenceExample/a", "id", "1");
      assertElementHasAttribute(text, "/stringReferenceExample/b", "reference", "1");
      assertElementHasAttribute(text, "/stringReferenceExample/c", "reference", "1");
       
      validate(example, persister);
      
      example = new StringReferenceExample("a", "b", "a");      
      out = new StringWriter();
      persister.write(example, out);
      text = out.toString();

      assertElementHasAttribute(text, "/stringReferenceExample", "id", "0");
      assertElementHasAttribute(text, "/stringReferenceExample/a", "id", "1");
      assertElementHasAttribute(text, "/stringReferenceExample/b", "id", "2");
      assertElementHasAttribute(text, "/stringReferenceExample/c", "reference", "1");
      
      validate(example, persister);  
      
      Integer one = new Integer(1);
      Integer two = new Integer(1);
      Integer three = new Integer(1);
      IntegerReferenceExample integers = new IntegerReferenceExample(one, two, three);
      
      out = new StringWriter();
      persister.write(integers, out);
      text = out.toString();

      assertElementHasAttribute(text, "/integerReferenceExample", "id", "0");
      assertElementHasAttribute(text, "/integerReferenceExample/a", "id", "1");
      assertElementHasAttribute(text, "/integerReferenceExample/b", "id", "2");
      assertElementHasAttribute(text, "/integerReferenceExample/c", "id", "3");
     
      validate(integers, persister);
      
      integers = new IntegerReferenceExample(one, one, two);   
      out = new StringWriter();
      persister.write(integers, out);
      text = out.toString();

      assertElementHasAttribute(text, "/integerReferenceExample", "id", "0");
      assertElementHasAttribute(text, "/integerReferenceExample/a", "id", "1");
      assertElementHasAttribute(text, "/integerReferenceExample/b", "reference", "1");
      assertElementHasAttribute(text, "/integerReferenceExample/c", "id", "2");
      
      validate(integers, persister);
   }
}
