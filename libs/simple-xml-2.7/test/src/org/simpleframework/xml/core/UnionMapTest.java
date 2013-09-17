
package org.simpleframework.xml.core;

import java.util.Map;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementMapUnion;

public class UnionMapTest extends ValidationTestCase {
   
   private static final String INLINE_MAP_SOURCE =
   "<inlineMapExample>" +
   "  <xEntry xKey='1'><x><x>A</x></x></xEntry>\n" +
   "  <yEntry yKey='2'><y><y>B</y></y></yEntry>\n" +
   "  <yEntry yKey='3'><y><y>C</y></y></yEntry>\n" +
   "  <zEntry zKey='4'><z><z>D</z></z></zEntry>\n" +
   "  <xEntry xKey='5'><x><x>E</x></x></xEntry>\n" +
   "</inlineMapExample>";     
   
   private static final String X_MAP_SOURCE =
   "<mapExample>" +
   "  <xMap>\n"+
   "     <xx key='1'><x><x>A</x></x></xx>\n" +
   "     <xx key='2'><x><x>B</x></x></xx>\n" +
   "  </xMap>\n"+
   "</mapExample>"; 
   
   private static final String Y_MAP_SOURCE =
   "<mapExample>" +
   "  <yMap>\n"+
   "     <yy key='1'><y><y>A</y></y></yy>\n" +
   "     <yy key='2'><y><y>B</y></y></yy>\n" +
   "  </yMap>\n"+
   "</mapExample>";  
   
   @Default
   public static class X implements Node {
      private String x;
      public String name() {
         return x;
      }
   }
   
   @Default
   public static class Y implements Node {
      private String y;
      public String name() {
         return y;
      }
   }
   
   @Default
   public static class Z implements Node {
      private String z;
      public String name() {
         return z;
      }
   }
   
   public static interface Node {
      public String name();
   }
   
   @Root
   public static class InlineMapExample {   
      @ElementMapUnion({
         @ElementMap(entry="xEntry", key="xKey", attribute=true, inline=true, keyType=String.class, valueType=X.class),
         @ElementMap(entry="yEntry", key="yKey", attribute=true, inline=true, keyType=String.class, valueType=Y.class),
         @ElementMap(entry="zEntry", key="zKey", attribute=true, inline=true, keyType=String.class, valueType=Z.class)
      })
      private Map<String, Node> map;
   }
   
   @Root
   public static class MapExample {   
      @ElementMapUnion({
         @ElementMap(name="xMap", entry="xx", key="key", attribute=true, keyType=String.class, valueType=X.class),
         @ElementMap(name="yMap", entry="yy", key="key", attribute=true, keyType=String.class, valueType=Y.class),
         @ElementMap(name="zMap", entry="zz", key="key", attribute=true, keyType=String.class, valueType=Z.class)
      })
      private Map<String, Node> map;
   }
   
   public void testInlineList() throws Exception {
      Persister persister = new Persister();
      InlineMapExample example = persister.read(InlineMapExample.class, INLINE_MAP_SOURCE);
      Map<String, Node> map = example.map;
      assertEquals(map.size(), 5);
      assertEquals(map.get("1").getClass(), X.class);
      assertEquals(map.get("1").name(), "A");
      assertEquals(map.get("2").getClass(), Y.class);
      assertEquals(map.get("2").name(), "B");
      assertEquals(map.get("3").getClass(), Y.class);
      assertEquals(map.get("3").name(), "C");
      assertEquals(map.get("4").getClass(), Z.class);
      assertEquals(map.get("4").name(), "D");
      assertEquals(map.get("5").getClass(), X.class);
      assertEquals(map.get("5").name(), "E");      
      validate(persister, example);
   }
   
   public void testXList() throws Exception {
      Persister persister = new Persister();
      MapExample example = persister.read(MapExample.class, X_MAP_SOURCE);
      Map<String, Node> map = example.map;
      assertEquals(map.size(), 2);
      assertEquals(map.get("1").getClass(), X.class);
      assertEquals(map.get("1").name(), "A");
      assertEquals(map.get("2").getClass(), X.class);
      assertEquals(map.get("2").name(), "B");     
      validate(persister, example);
   }
   
   public void testYList() throws Exception {
      Persister persister = new Persister();
      MapExample example = persister.read(MapExample.class, Y_MAP_SOURCE);
      Map<String, Node> map = example.map;
      assertEquals(map.size(), 2);
      assertEquals(map.get("1").getClass(), Y.class);
      assertEquals(map.get("1").name(), "A");
      assertEquals(map.get("2").getClass(), Y.class);
      assertEquals(map.get("2").name(), "B");     
      validate(persister, example);
   }
}
