package org.simpleframework.xml.core;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;

public class ExceptionForArrayTest extends ValidationTestCase {

   private final String INVALID_PRIMITIVE_LENGTH = "<a><b length='0'><x>1</x><x>2</x></b></a>";
   private final String COMPOSITE_PRIMITIVE_LENGTH = "<a><e length='0'><x v='1'>1</x><x v='2'>2</x></e></a>";
   @Root   
   private static class A{
      @ElementArray(entry="x")
      private int[] b;
   }
   @Root
   private static class B {
      @ElementArray(entry="x")
      private E[] e;
   }
   @Root
   private static class E{
      @Attribute
      private String v;
      @Text
      private String t;
   }
   public void testArrayLength() throws Exception {
      Persister persister = new Persister();
      boolean exception = false;
      try {
         A a = persister.read(A.class, INVALID_PRIMITIVE_LENGTH);
      }catch(ElementException e){
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Invalid array length is an exception", exception);      
   }
   public void testCompositeArrayLength() throws Exception {
      Persister persister = new Persister();
      boolean exception = false;
      try {
         B b = persister.read(B.class, COMPOSITE_PRIMITIVE_LENGTH);
      }catch(ElementException e){
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Invalid array length is an exception", exception);      
   }
}
