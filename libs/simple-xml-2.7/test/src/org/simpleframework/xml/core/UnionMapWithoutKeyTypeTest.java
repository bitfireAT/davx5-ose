package org.simpleframework.xml.core;

import java.util.Map;

import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementMapUnion;

public class UnionMapWithoutKeyTypeTest extends ValidationTestCase {
   
   private static final String SOURCE =
   "<mapExample>" +
   "  <x-node key='1'><x>A</x></x-node>\n" +
   "  <y-node key='2'><y>B</y></y-node>\n" +
   "  <y-node key='3'><y>C</y></y-node>\n" +
   "  <z-node key='4'><z>D</z></z-node>\n" +
   "</mapExample>";     
      
   @Root
   public static class X implements Node {
      @Text
      private String x;
      public String name() {
         return x;
      }
   }
   
   @Root
   public static class Y implements Node {
      @Text
      private String y;
      public String name() {
         return y;
      }
   }
   
   @Root
   public static class Z implements Node {
      @Text
      private String z;
      public String name() {
         return z;
      }
   }
   
   public static interface Node {
      public String name();
   }
   
   @Root
   public static class MapExample {   
      @ElementMapUnion({
         @ElementMap(entry="x-node", key="key", inline=true, attribute=true, valueType=X.class),
         @ElementMap(entry="y-node", key="key", inline=true, attribute=true, valueType=Y.class),
         @ElementMap(entry="z-node", key="key", inline=true, attribute=true, valueType=Z.class)
      })
      private Map<String, Node> map;
   }
   
   public void testInlineList() throws Exception {
      Persister persister = new Persister();
      MapExample example = persister.read(MapExample.class, SOURCE);
      Map<String, Node> map = example.map;
      assertEquals(map.size(), 4);
      assertEquals(map.get("1").getClass(), X.class);
      assertEquals(map.get("1").name(), "A");
      assertEquals(map.get("2").getClass(), Y.class);
      assertEquals(map.get("2").name(), "B");
      assertEquals(map.get("3").getClass(), Y.class);
      assertEquals(map.get("3").name(), "C");
      assertEquals(map.get("4").getClass(), Z.class);
      assertEquals(map.get("4").name(), "D");      
      validate(persister, example);
   }   
}
