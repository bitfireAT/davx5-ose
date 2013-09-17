package org.simpleframework.xml.core;

import java.lang.reflect.Constructor;
import java.util.List;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.core.ConstructorInjectionWithUnionTest.X;
import org.simpleframework.xml.core.ConstructorInjectionWithUnionTest.Y;
import org.simpleframework.xml.stream.Format;

@SuppressWarnings("all")
public class SignatureScannerTest extends ScannerCreatorTest {

   private static class UnionExample {
      public UnionExample(            
            @ElementUnion({
               @Element(name="a", type=String.class),
               @Element(name="b", type=Integer.class),
               @Element(name="c", type=Long.class)
            }) 
            Object a) {}
   }
   
   private static class UnionBigPermutationExample {
      public UnionBigPermutationExample(
            @ElementUnion({
               @Element(name="a", type=String.class),
               @Element(name="b", type=Integer.class),
               @Element(name="c", type=Long.class)
            }) 
            Object a,
            
            @ElementUnion({
               @Element(name="x", type=String.class),
               @Element(name="y", type=Integer.class),
               @Element(name="z", type=Long.class)
            }) 
            Object b) {}
   }
   
   public void testUnion() throws Exception {
      Constructor factory = UnionExample.class.getDeclaredConstructor(Object.class);
      ParameterMap registry = new ParameterMap();
      Format format = new Format();
      SignatureScanner scanner = new SignatureScanner(factory, registry, new Support());
      List<Signature> signatures = scanner.getSignatures();
      
      assertTrue(scanner.isValid());
      assertEquals(signatures.size(), 3);
   }
   
   public void testUnionBigPermutation() throws Exception {
      Constructor factory = UnionBigPermutationExample.class.getDeclaredConstructor(Object.class, Object.class);
      ParameterMap registry = new ParameterMap();
      Format format = new Format();
      SignatureScanner scanner = new SignatureScanner(factory, registry, new Support());
      List<Signature> signatures = scanner.getSignatures();
      
      assertTrue(scanner.isValid());
      assertEquals(signatures.size(), 9);
      
      assertEquals(findSignature(signatures, String.class, String.class).get(0).getName(), "a");
      assertEquals(findSignature(signatures, String.class, String.class).get(1).getName(), "x");
      assertEquals(findSignature(signatures, String.class, String.class).get(0).getType(), String.class);
      assertEquals(findSignature(signatures, String.class, String.class).get(1).getType(), String.class);
      
      assertEquals(findSignature(signatures, Integer.class, String.class).get(0).getName(), "b");
      assertEquals(findSignature(signatures, Integer.class, String.class).get(1).getName(), "x");
      assertEquals(findSignature(signatures, Integer.class, String.class).get(0).getType(), Integer.class);
      assertEquals(findSignature(signatures, Integer.class, String.class).get(1).getType(), String.class);
      
      assertEquals(findSignature(signatures, Long.class, String.class).get(0).getName(), "c");
      assertEquals(findSignature(signatures, Long.class, String.class).get(1).getName(), "x");
      assertEquals(findSignature(signatures, Long.class, String.class).get(0).getType(), Long.class);
      assertEquals(findSignature(signatures, Long.class, String.class).get(1).getType(), String.class);
      
      assertEquals(findSignature(signatures, String.class, Integer.class).get(0).getName(), "a");
      assertEquals(findSignature(signatures, String.class, Integer.class).get(1).getName(), "y");
      assertEquals(findSignature(signatures, String.class, Integer.class).get(0).getType(), String.class);
      assertEquals(findSignature(signatures, String.class, Integer.class).get(1).getType(), Integer.class);
      
      assertEquals(findSignature(signatures, Integer.class, Integer.class).get(0).getName(), "b");
      assertEquals(findSignature(signatures, Integer.class, Integer.class).get(1).getName(), "y");
      assertEquals(findSignature(signatures, Integer.class, Integer.class).get(0).getType(), Integer.class);
      assertEquals(findSignature(signatures, Integer.class, Integer.class).get(1).getType(), Integer.class);
      
      assertEquals(findSignature(signatures, Long.class, Integer.class).get(0).getName(), "c");
      assertEquals(findSignature(signatures, Long.class, Integer.class).get(1).getName(), "y");
      assertEquals(findSignature(signatures, Long.class, Integer.class).get(0).getType(), Long.class);
      assertEquals(findSignature(signatures, Long.class, Integer.class).get(1).getType(), Integer.class);
   }
   
   private Signature findSignature(List<Signature> list, Class... types) {
      for(Signature signature : list) {
         List<Parameter> params = signature.getAll();
         int count = params.size();
         
         for(int i = 0; i < types.length; i++) {
            Parameter param = params.get(i);
            Class type = param.getType();
            
            if(type == types[i]) {
               count--;
         }
         if(count == 0) {
            return signature;
            }
         }
      }
      return null;
   }
}
