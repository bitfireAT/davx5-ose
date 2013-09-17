package org.simpleframework.xml.core;

import java.util.Date;
import java.util.Map;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Transient;
import org.simpleframework.xml.ValidationTestCase;

public class MultiElementMapTest extends ValidationTestCase {
   
   private static final String SOURCE =
   "<properties>\n"+
   "    <entry>\n"+
   "        <key>boolean-value</key>\n"+
   "        <value>\n"+
   "            <boolean>true</boolean>\n"+
   "        </value>\n"+
   "    </entry>\n"+
   "    <entry>\n"+
   "        <key>string-value</key>\n"+
   "        <value>\n"+
   "            <string>hello world</string>\n"+
   "        </value>\n"+
   "    </entry>\n"+
   "    <entry>\n"+
   "        <key>int-value</key>\n"+
   "        <value>\n"+
   "            <int>42</int>\n"+
   "        </value>\n"+
   "    </entry>\n"+
   "</properties>";

   @Root
   private static class Value {
      
      @Element(name="boolean", required=false)
      private Boolean booleanValue;

      @Element(name="byte", required=false)
      private Byte byteValue; 

      @Element(name="double", required=false)
      private Double doubleValue; 

      @Element(name="float", required=false)
      private Float floatValue;

      @Element(name="int", required=false)
      private Integer intValue;

      @Element(name="long", required=false)
      private Long longValue;

      @Element(name="short", required=false)
      private Short shortValue;

      @Element(name="dateTime", required=false)
      private Date dateTime; 

      @Element(name="string", required=false)
      private String string;
      
      @Transient
      private Object value;
      
      @Validate
      private void commit() {
         if(booleanValue != null) {
            value = booleanValue;
         }
         if(byteValue != null) {
            value = byteValue;
         } 
         if(doubleValue != null) {
            value = doubleValue;
         } 
         if(floatValue != null) {
            value = floatValue;
         }
         if(intValue != null) {
            value = intValue;
         }
         if(longValue != null) {
            value = longValue;
         }
         if(shortValue != null) {
            value = shortValue;
         }
         if(dateTime != null) {
            value = dateTime;
         } 
         if(string != null) {
            value = string;
         }
      }
      
      public Object get() {
         return value;
      }
      
      public void set(Object value) {
         this.value = value;
      }
   }
   
   @Root
   private static class Properties {
      
      @ElementMap(key="key", value="value", inline=true)
      private Map<String, Value> map;
      
      public Object get(String name) {
         return map.get(name).get();
      }
   }
   
   
   
   public void testProperties() throws Exception {
      Persister persister = new Persister();
      Properties properties = persister.read(Properties.class, SOURCE);
      
      assertEquals(true, properties.get("boolean-value"));
      assertEquals("hello world", properties.get("string-value"));
      assertEquals(42, properties.get("int-value"));
      
      validate(persister, properties);
   }
}
