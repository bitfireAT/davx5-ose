
package org.simpleframework.xml.core;

import java.util.List;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementListUnion;

public class UnionListTest extends ValidationTestCase {
   
   private static final String INLINE_LIST_SOURCE =
   "<inlineListExample>" +
   "  <xx><x>A</x></xx>\n" +
   "  <yy><y>B</y></yy>\n" +
   "  <yy><y>C</y></yy>\n" +
   "  <zz><z>D</z></zz>\n" +
   "  <xx><x>E</x></xx>\n" +
   "</inlineListExample>";     
   
   private static final String X_LIST_SOURCE =
   "<listExample>" +
   "  <xList>\n"+
   "     <xx><x>A</x></xx>\n" +
   "     <xx><x>B</x></xx>\n" +
   "  </xList>\n"+
   "</listExample>"; 
   
   private static final String Y_LIST_SOURCE =
   "<listExample>" +
   "  <yList>\n"+
   "     <yy><y>A</y></yy>\n" +
   "     <yy><y>B</y></yy>\n" +
   "  </yList>\n"+
   "</listExample>";  
   
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
   public static class InlineListExample {   
      @ElementListUnion({
         @ElementList(entry="xx", inline=true, type=X.class),
         @ElementList(entry="yy", inline=true, type=Y.class),
         @ElementList(entry="zz", inline=true, type=Z.class)
      })
      private List<Node> list;
   }
   
   @Root
   public static class ListExample {   
      @ElementListUnion({
         @ElementList(name="xList", entry="xx", type=X.class),
         @ElementList(name="yList", entry="yy", type=Y.class),
         @ElementList(name="zList", entry="zz", type=Z.class)
      })
      private List<Node> list;
   }
   
   public void testInlineList() throws Exception {
      Persister persister = new Persister();
      InlineListExample example = persister.read(InlineListExample.class, INLINE_LIST_SOURCE);
      List<Node> list = example.list;
      assertEquals(list.size(), 5);
      assertEquals(list.get(0).getClass(), X.class);
      assertEquals(list.get(0).name(), "A");
      assertEquals(list.get(1).getClass(), Y.class);
      assertEquals(list.get(1).name(), "B");
      assertEquals(list.get(2).getClass(), Y.class);
      assertEquals(list.get(2).name(), "C");
      assertEquals(list.get(3).getClass(), Z.class);
      assertEquals(list.get(3).name(), "D");
      assertEquals(list.get(4).getClass(), X.class);
      assertEquals(list.get(4).name(), "E");      
      validate(persister, example);
   }
   
   public void testXList() throws Exception {
      Persister persister = new Persister();
      ListExample example = persister.read(ListExample.class, X_LIST_SOURCE);
      List<Node> list = example.list;
      assertEquals(list.size(), 2);
      assertEquals(list.get(0).getClass(), X.class);
      assertEquals(list.get(0).name(), "A");
      assertEquals(list.get(1).getClass(), X.class);
      assertEquals(list.get(1).name(), "B");     
      validate(persister, example);
   }
   
   public void testYList() throws Exception {
      Persister persister = new Persister();
      ListExample example = persister.read(ListExample.class, Y_LIST_SOURCE);
      List<Node> list = example.list;
      assertEquals(list.size(), 2);
      assertEquals(list.get(0).getClass(), Y.class);
      assertEquals(list.get(0).name(), "A");
      assertEquals(list.get(1).getClass(), Y.class);
      assertEquals(list.get(1).name(), "B");     
      validate(persister, example);
   }
}
