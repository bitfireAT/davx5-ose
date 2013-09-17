package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;

public class MapNullTest extends ValidationTestCase {
  
   private static final String EMPTY_AS_NULL = 
   "<complexMap>\r\n" +
   "   <map class='java.util.HashMap'>\r\n" +
   "      <entry>\r\n" +
   "         <mapEntry>\r\n" +
   "            <name>3</name>\r\n" +
   "            <value>3</value>\r\n" +
   "         </mapEntry>\r\n" +
   "      </entry>\r\n" +
   "      <entry>\r\n" +
   "         <compositeKey>\r\n" +
   "            <name>name.1</name>\r\n" +
   "            <address>address.1</address>\r\n" +
   "         </compositeKey>\r\n" +
   "         <mapEntry>\r\n" +
   "            <name>1</name>\r\n" +
   "            <value>1</value>\r\n" +
   "         </mapEntry>\r\n" +
   "      </entry>\r\n" +
   "      <entry>\r\n" +
   "         <compositeKey>\r\n" +
   "            <name>name.2</name>\r\n" +
   "            <address>address.2</address>\r\n" +
   "         </compositeKey>\r\n" +
   "         <mapEntry>\r\n" +
   "            <name>2</name>\r\n" +
   "            <value>2</value>\r\n" +
   "         </mapEntry>\r\n" +
   "      </entry>\r\n" +
   "      <entry>\r\n" +
   "         <compositeKey>\r\n" +
   "            <name>name.4</name>\r\n" +
   "            <address>address.4</address>\r\n" +
   "         </compositeKey>\r\n" +
   "      </entry>\r\n" +
   "   </map>\r\n" +
   "</complexMap>\r\n";
   
   private static final String EMPTY_COMPOSITE_VALUE =
   "<complexMap>\r\n" +
   "   <map class='java.util.HashMap'>\r\n" +
   "      <entry>\r\n" +
   "         <compositeKey>\r\n" +
   "            <name>name.4</name>\r\n" +
   "            <address>address.4</address>\r\n" +
   "         </compositeKey>\r\n" +
   "      </entry>\r\n" +
   "   </map>\r\n" +
   "</complexMap>\r\n";
   
   private static final String EMPTY_COMPOSITE_BLANK_VALUE =
   "<complexMap>\r\n" +
   "   <map class='java.util.HashMap'>\r\n" +
   "      <entry>\r\n" +
   "         <compositeKey>\r\n" +
   "            <name>name.4</name>\r\n" +
   "            <address>address.4</address>\r\n" +
   "         </compositeKey>\r\n" +
   "         <mapEntry/>\r\n" +
   "      </entry>\r\n" +
   "   </map>\r\n" +
   "</complexMap>\r\n";
   
   private static final String EMPTY_COMPOSITE_KEY =
   "<complexMap>\r\n" +
   "   <map class='java.util.HashMap'>\r\n" +
   "      <entry>\r\n" +
   "         <mapEntry>\r\n" +
   "            <name>3</name>\r\n" +
   "            <value>3</value>\r\n" +
   "         </mapEntry>\r\n" +
   "      </entry>\r\n" +
   "   </map>\r\n" +
   "</complexMap>\r\n";     
   
   private static final String EMPTY_COMPOSITE_BLANK_KEY =
   "<complexMap>\r\n" +
   "   <map class='java.util.HashMap'>\r\n" +
   "      <entry>\r\n" +
   "         <compositeKey/>\r\n" +
   "         <mapEntry>\r\n" +
   "            <name>3</name>\r\n" +
   "            <value>3</value>\r\n" +
   "         </mapEntry>\r\n" +
   "      </entry>\r\n" +
   "   </map>\r\n" +
   "</complexMap>\r\n";
   
   private static final String EMPTY_PRIMITIVE_VALUE =
   "<primitiveMap>\r\n" +
   "   <table class='java.util.HashMap'>\r\n" +
   "      <entry>\r\n" +
   "         <string>example</string>\r\n" +            
   "      </entry>\r\n" +
   "   </table>\r\n" +
   "</primitiveMap>\r\n";
   
   private static final String EMPTY_PRIMITIVE_BLANK_VALUE =
   "<primitiveMap>\r\n" +
   "   <table class='java.util.HashMap'>\r\n" +
   "      <entry>\r\n" +
   "         <string>example</string>\r\n" + 
   "         <bigDecimal/>\r\n" +
   "      </entry>\r\n" +
   "   </table>\r\n" +
   "</primitiveMap>\r\n";
   
   private static final String EMPTY_PRIMITIVE_KEY =
   "<primitiveMap>\r\n" +
   "   <table class='java.util.HashMap'>\r\n" +
   "      <entry>\r\n" +
   "         <bigDecimal>4</bigDecimal>\r\n" +
   "      </entry>\r\n" +
   "   </table>\r\n" +
   "</primitiveMap>\r\n";
   
   private static final String EMPTY_PRIMITIVE_BLANK_KEY =
   "<primitiveMap>\r\n" +
   "   <table class='java.util.HashMap'>\r\n" +
   "      <entry>\r\n" +
   "         <string/>\r\n" +
   "         <bigDecimal>4</bigDecimal>\r\n" +
   "      </entry>\r\n" +
   "   </table>\r\n" +
   "</primitiveMap>\r\n";
   
   @Root
   private static class MapEntry {
      
      @Element
      private String name;
      
      @Element
      private String value;
      
      public MapEntry() {
         super();
      }
      
      public MapEntry(String name, String value) {
         this.name = name;
         this.value = value;
      }
      
      public boolean equals(Object other) {
         if(other instanceof MapEntry) {
            MapEntry entry = (MapEntry) other;
            
            if(entry.value.equals(value)) {
               return entry.name.equals(name);
            }
         }
         return false;         
      }
   }
   
   @Root
   private static class ComplexMap {      
      
      @ElementMap
      private Map<CompositeKey, MapEntry> map;
      
      public ComplexMap() {
         this.map = new HashMap<CompositeKey, MapEntry>();
      }
      
      public String getValue(CompositeKey key) {
         MapEntry entry = map.get(key);
         
         if(entry != null) {
            return entry.value;
         }
         return null;
      }
   }
   
   @Root
   private static class CompositeKey {
      
      @Element
      private String name;
      
      @Element
      private String address;
      
      public CompositeKey() {
         super();
      }
      
      public CompositeKey(String name, String address) {
         this.name = name;
         this.address = address;
      }
      
      public int hashCode() {         
         return name.hashCode() + address.hashCode();
      }
      
      public boolean equals(Object item) {
         if(item instanceof CompositeKey) {
            CompositeKey other = (CompositeKey)item;
            
            return other.name.equals(name) && other.address.equals(address);
         }
         return false;
      }
   }
   
   @Root
   private static class PrimitiveMap {
      
      @ElementMap(name="table")
      private Map<String, BigDecimal> map;
      
      public PrimitiveMap() {
         this.map = new HashMap<String, BigDecimal>();         
      }
      
      public BigDecimal getValue(String name) {
         return map.get(name);
      }
   }
   
   public void testEmptyCompositeValue() throws Exception {
      Serializer serializer = new Persister();
      ComplexMap value = serializer.read(ComplexMap.class, EMPTY_COMPOSITE_VALUE);
      boolean valid = serializer.validate(ComplexMap.class, EMPTY_COMPOSITE_VALUE);
      
      assertTrue(valid);
      
      validate(value, serializer);
   }
   
   public void testEmptyCompositeBlankValue() throws Exception {
      Serializer serializer = new Persister();
      ComplexMap value = serializer.read(ComplexMap.class, EMPTY_COMPOSITE_BLANK_VALUE);
      boolean valid = serializer.validate(ComplexMap.class, EMPTY_COMPOSITE_BLANK_VALUE);
      
      assertTrue(valid);
      
      validate(value, serializer);
   }
   
   public void testEmptyCompositeKey() throws Exception {
      Serializer serializer = new Persister();
      ComplexMap value = serializer.read(ComplexMap.class, EMPTY_COMPOSITE_KEY);
      boolean valid = serializer.validate(ComplexMap.class, EMPTY_COMPOSITE_KEY);
      
      assertTrue(valid);
      
      validate(value, serializer);      
   }
   
   public void testEmptyCompositeBlankKey() throws Exception {
      Serializer serializer = new Persister();
      ComplexMap value = serializer.read(ComplexMap.class, EMPTY_COMPOSITE_BLANK_KEY);     
      boolean valid = serializer.validate(ComplexMap.class, EMPTY_COMPOSITE_BLANK_KEY);
      
      assertTrue(valid);
      
      validate(value, serializer);      
   }
   
   public void testEmptyPrimitiveValue() throws Exception {
      Serializer serializer = new Persister();
      PrimitiveMap value = serializer.read(PrimitiveMap.class, EMPTY_PRIMITIVE_VALUE);     
      boolean valid = serializer.validate(PrimitiveMap.class, EMPTY_PRIMITIVE_VALUE);
      
      assertTrue(valid);
      
      validate(value, serializer);      
   }
   
   public void testEmptyPrimitiveBlankValue() throws Exception {
      Serializer serializer = new Persister();
      PrimitiveMap value = serializer.read(PrimitiveMap.class, EMPTY_PRIMITIVE_BLANK_VALUE);     
      boolean valid = serializer.validate(PrimitiveMap.class, EMPTY_PRIMITIVE_BLANK_VALUE);
      
      assertTrue(valid);
      
      validate(value, serializer);      
   }
   
   public void testEmptyPrimitiveKey() throws Exception {
      Serializer serializer = new Persister();
      PrimitiveMap value = serializer.read(PrimitiveMap.class, EMPTY_PRIMITIVE_KEY);     
      boolean valid = serializer.validate(PrimitiveMap.class, EMPTY_PRIMITIVE_KEY);
      
      assertTrue(valid);
      
      validate(value, serializer);      
   }
   
   public void testEmptyPrimitiveBlankKey() throws Exception {
      Serializer serializer = new Persister();
      PrimitiveMap value = serializer.read(PrimitiveMap.class, EMPTY_PRIMITIVE_BLANK_KEY);     
      boolean valid = serializer.validate(PrimitiveMap.class, EMPTY_PRIMITIVE_BLANK_KEY);
      
      assertTrue(valid);
      
      validate(value, serializer);      
   }
   
   public void testNullValue() throws Exception {
      Serializer serializer = new Persister();
      PrimitiveMap primitiveMap = new PrimitiveMap();
      
      primitiveMap.map.put("a", new BigDecimal(1));
      primitiveMap.map.put("b", new BigDecimal(2));
      primitiveMap.map.put("c", null);     
      primitiveMap.map.put(null, new BigDecimal(4));
      
      StringWriter out = new StringWriter();
      serializer.write(primitiveMap, out);
      
      primitiveMap = serializer.read(PrimitiveMap.class, out.toString());
      
      assertEquals(primitiveMap.map.get(null), new BigDecimal(4));
      assertEquals(primitiveMap.map.get("c"), null);
      assertEquals(primitiveMap.map.get("a"), new BigDecimal(1));
      assertEquals(primitiveMap.map.get("b"), new BigDecimal(2));
      
      validate(primitiveMap, serializer);           
      
      ComplexMap complexMap = new ComplexMap();
      
      complexMap.map.put(new CompositeKey("name.1", "address.1"), new MapEntry("1", "1"));
      complexMap.map.put(new CompositeKey("name.2", "address.2"), new MapEntry("2", "2"));
      complexMap.map.put(null, new MapEntry("3", "3"));
      complexMap.map.put(new CompositeKey("name.4", "address.4"), null);
      
      validate(complexMap, serializer);
      
      ComplexMap emptyNull = serializer.read(ComplexMap.class, EMPTY_AS_NULL);
      
      assertEquals(emptyNull.getValue(new CompositeKey("name.1", "address.1")), "1");
      assertEquals(emptyNull.getValue(new CompositeKey("name.2", "address.2")), "2");
      assertEquals(emptyNull.getValue(null), "3");
      assertEquals(emptyNull.getValue(new CompositeKey("name.4", "address.4")), null);
      
      validate(emptyNull, serializer);
   }
   
   // TODO test the null values and exceptions with the map
}
