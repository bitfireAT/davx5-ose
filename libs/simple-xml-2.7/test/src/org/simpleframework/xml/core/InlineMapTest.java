package org.simpleframework.xml.core;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;

public class InlineMapTest extends ValidationTestCase {
   
   @Root
   private static class PrimitiveInlineMap {
      
      @ElementMap(entry="entity", inline=true)
      private Map<String, BigDecimal> map;
      
      public PrimitiveInlineMap() {
         this.map = new HashMap<String, BigDecimal>();
      }
      
      public BigDecimal getValue(String name) {
         return map.get(name);
      }
   }
   
   @Root
   private static class PrimitiveInlineAttributeMap {
      
      @ElementMap(entry="entity", attribute=true, inline=true)
      private Map<String, BigDecimal> map;
      
      public PrimitiveInlineAttributeMap() {
         this.map = new HashMap<String, BigDecimal>();
      }
      
      public BigDecimal getValue(String name) {
         return map.get(name);
      }
   }
   
   @Root
   private static class PrimitiveInlineAttributeValueMap {
      
      @ElementMap(entry="entity", value="value", attribute=true, inline=true)
      private Map<String, BigDecimal> map;
      
      public PrimitiveInlineAttributeValueMap() {
         this.map = new HashMap<String, BigDecimal>();
      }
      
      public BigDecimal getValue(String name) {
         return map.get(name);
      }
   }
   
   public void testPrimitiveMap() throws Exception {
      PrimitiveInlineMap map = new PrimitiveInlineMap();
      Serializer serializer = new Persister();
      
      map.map.put("a", new BigDecimal(1.1));
      map.map.put("b", new BigDecimal(2.2));
      
      validate(map, serializer);
   }
   
   public void testPrimitiveAttributeMap() throws Exception {
      PrimitiveInlineAttributeMap map = new PrimitiveInlineAttributeMap();
      Serializer serializer = new Persister();
      
      map.map.put("a", new BigDecimal(1.1));
      map.map.put("b", null);
      map.map.put("c", new BigDecimal(2.2));
      map.map.put("d", null);
      
      validate(map, serializer);
   }
   
   public void testPrimitiveAttributeValueMap() throws Exception {
      PrimitiveInlineAttributeValueMap map = new PrimitiveInlineAttributeValueMap();
      Serializer serializer = new Persister();
      
      map.map.put("a", new BigDecimal(1.1));
      map.map.put("b", new BigDecimal(2.2));
      map.map.put(null, new BigDecimal(3.3));
      map.map.put("d", null);
      
      validate(map, serializer);
   }
}
