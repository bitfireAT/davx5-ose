package org.simpleframework.xml.core;

import java.util.List;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class UnionElementListInConstructorTest extends ValidationTestCase {
   
   private static final String SOURCE =
   "<unionExample xmlns:x='http://x.com/x' xmlns:y='http://y.com/y'>\n" + 
   "   <x:path>\n" + 
   "      <a/>\n" + 
   "      <a/>\n" + 
   "      <b/>\n" + 
   "      <a/>\n" + 
   "      <c/>\n" + 
   "   </x:path>\n" + 
   "   <y:path>\n" + 
   "      <a/>\n" + 
   "      <a/>\n" + 
   "      <b/>\n" + 
   "      <a/>\n" + 
   "      <c/>\n" + 
   "   </y:path>\n" + 
   "</unionExample>";       
   
   @Root
   @NamespaceList({
      @Namespace(prefix="x", reference="http://x.com/x"),
      @Namespace(prefix="y", reference="http://y.com/y")
   })
   
   public static class InlineListUnion {
      
      @Path("x:path[1]")
      @ElementListUnion({
         @ElementList(entry="a", type=A.class, inline=true),
         @ElementList(entry="b", type=B.class, inline=true),
         @ElementList(entry="c", type=C.class, inline=true)
      })
      private final List<Entry> one;
      
      @Path("y:path[2]")
      @ElementListUnion({
         @ElementList(entry="a", type=A.class, inline=true),
         @ElementList(entry="b", type=B.class, inline=true),
         @ElementList(entry="c", type=C.class, inline=true)
      })
      private final List<Entry> two;
      
      public InlineListUnion(
            @Path("x:path[1]") @ElementList(name="a") List<Entry> one,
            @Path("y:path[2]") @ElementList(name="a") List<Entry> two)
      {
         this.one = one;
         this.two = two;
      }
            
   }
   
   private static interface Entry {
      public String getType();
   }
   
   @Root
   public static class A implements Entry {
      public String getType() {
         return "A";
      }
   }
   
   @Root
   public static class B implements Entry {
      public String getType() {
         return "B";
      }
   }
   
   @Root
   public static class C implements Entry {
      public String getType() {
         return "C";
      }
   }

   public void testListUnion() throws Exception {
      Persister persister = new Persister();
      InlineListUnion union = persister.read(InlineListUnion.class, SOURCE);
      assertEquals(union.one.get(0).getClass(), A.class);
      assertEquals(union.one.get(1).getClass(), A.class);
      assertEquals(union.one.get(2).getClass(), B.class);
      assertEquals(union.one.get(3).getClass(), A.class);
      assertEquals(union.one.get(4).getClass(), C.class);
      assertEquals(union.two.get(0).getClass(), A.class);
      assertEquals(union.two.get(1).getClass(), A.class);
      assertEquals(union.two.get(2).getClass(), B.class);
      assertEquals(union.two.get(3).getClass(), A.class);
      assertEquals(union.two.get(4).getClass(), C.class);
      validate(persister, union);
   }

}
