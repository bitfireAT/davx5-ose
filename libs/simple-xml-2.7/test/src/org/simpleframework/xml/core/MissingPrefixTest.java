package org.simpleframework.xml.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import junit.framework.TestCase;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.stream.NodeBuilder;

public class MissingPrefixTest extends TestCase {
   
   private static final String BLAH =       
   "<blah>\n"+
   "   <d:foo>\n"+
   "      <x>x</x>\n"+
   "      <y>1</y>\n"+
   "      <z>2</z>\n"+
   "   </d:foo>\n"+
   "</blah>\n";


   @Root
   public static class Blah  {
      @Element
      private BlahBlah foo;
      public Blah() {
         super();
      }
      public Blah(String x, int y, long z) {
         this.foo = new BlahBlah(x, y, z);
      }
       
   }
   
   @Default
   public static class BlahBlah {
      private String x;
      private int y;
      private long z;
      public BlahBlah(
            @Element(name="x")String x, 
            @Element(name="y")int y, 
            @Element(name="z")long z) {
         this.x = x;
         this.y = y;
         this.z = z;
         
      }
   }
   
   public void testMissingPrefix() throws Exception {
      Object originalProvider = null;
      boolean failure = false;
      try {
         originalProvider = changeToPullProvider();
         
         Persister p = new Persister();
         Blah b = new Blah("x", 1, 2);
         Blah a = p.read(Blah.class, BLAH);
         
         assertEquals(a.foo.x, "x");
         assertEquals(a.foo.y, 1);
         assertEquals(a.foo.z, 2);
         
         p.write(b, System.out); 
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      } finally {
         setProviderTo(originalProvider);
      }
      assertTrue("Failure should have occured for unresolved prefix", failure);
   }
   
   private Object changeToPullProvider() throws Exception {
      Object provider = null;
      try {      
         Class c = Class.forName("org.simpleframework.xml.stream.PullProvider");
         Constructor[] cons = c.getDeclaredConstructors();
         for(Constructor con : cons) {
            con.setAccessible(true);
         }
         Object o = cons[0].newInstance();
         return setProviderTo(o);
      }catch(Exception e) {
         e.printStackTrace();
      }
      return provider;
   }
   
   private Object setProviderTo(Object value) throws Exception {
      Object provider = null;
      try {      
         Field f = NodeBuilder.class.getDeclaredField("provider");
         f.setAccessible(true);
         provider = f.get(null);
         f.set(null, value);
      }catch(Exception e) {
         e.printStackTrace();
      }
      return provider;
   }
   
   
}
