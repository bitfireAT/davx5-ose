package org.simpleframework.xml.convert;

import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;

import junit.framework.TestCase;

public class ConverterFactoryTest extends TestCase {
   
   private static class A implements Converter {
      public Object read(InputNode node) {
         return null;
      }
      public void write(OutputNode node, Object value) {
         return;
      }  
   }
   private static class B extends A {}
   private static class C extends A {}
   
   public void testFactory() throws Exception {
      ConverterFactory factory = new ConverterFactory();
      Converter a1 = factory.getInstance(A.class);
      Converter b1 = factory.getInstance(B.class);
      Converter c1 = factory.getInstance(C.class);
      Converter a2 = factory.getInstance(A.class);
      Converter b2 = factory.getInstance(B.class);
      Converter c2 = factory.getInstance(C.class);
      
      assertTrue(a1 == a2);
      assertTrue(b1 == b2);
      assertTrue(c1 == c2);
      assertEquals(a1.getClass(), A.class);
      assertEquals(b1.getClass(), B.class);
      assertEquals(c1.getClass(), C.class);
   }

}
