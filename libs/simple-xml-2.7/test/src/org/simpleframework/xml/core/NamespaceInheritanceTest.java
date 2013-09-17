package org.simpleframework.xml.core;

import java.io.ByteArrayOutputStream;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.ValidationTestCase;

public class NamespaceInheritanceTest extends ValidationTestCase {
   
   @Namespace(reference="namespace1")
   private static class Aaa {
      @Element(name="bbb", required=false)
      public Bbb bbb;
   }
 
   @Namespace(reference="namespace2")
   private static class Bbb {
      @Element(name="aaa", required=false)
      public Aaa aaa;
   }
   
   @Namespace(prefix="aaa", reference="namespace1")
   private static class AaaWithPrefix {
      @Element(name="bbb", required=false)
      public BbbWithPrefix bbb;
   }
 
   @Namespace(prefix="bbb", reference="namespace2")
   private static class BbbWithPrefix {
      @Element(name="aaa", required=false)
      public AaaWithPrefix aaa;
   }
   
   public void testNamespace() throws Exception {
      Aaa parent = new Aaa();
      Bbb child = new Bbb();
      parent.bbb = child;
      Aaa grandchild = new Aaa();
      child.aaa = grandchild;
      grandchild.bbb = new Bbb();
 
      ByteArrayOutputStream tmp = new ByteArrayOutputStream();
      Serializer serializer = new Persister();
      serializer.write(parent, tmp);
 
      String result = new String(tmp.toByteArray());
      System.out.println(result);
 
      assertElementHasAttribute(result, "/aaa", "xmlns", "namespace1");
      assertElementHasAttribute(result, "/aaa/bbb", "xmlns", "namespace2");
      assertElementHasAttribute(result, "/aaa/bbb/aaa", "xmlns", "namespace1");
      assertElementHasAttribute(result, "/aaa/bbb/aaa/bbb", "xmlns", "namespace2");
      
      assertElementHasNamespace(result, "/aaa", "namespace1");
      assertElementHasNamespace(result, "/aaa/bbb", "namespace2");
      assertElementHasNamespace(result, "/aaa/bbb/aaa", "namespace1");
      assertElementHasNamespace(result, "/aaa/bbb/aaa/bbb", "namespace2");
   }
   
   public void testNamespacePrefix() throws Exception {
      AaaWithPrefix parent = new AaaWithPrefix();
      BbbWithPrefix child = new BbbWithPrefix();
      parent.bbb = child;
      AaaWithPrefix grandchild = new AaaWithPrefix();
      child.aaa = grandchild;
      grandchild.bbb = new BbbWithPrefix();
 
      ByteArrayOutputStream tmp = new ByteArrayOutputStream();
      Serializer serializer = new Persister();
      serializer.write(parent, tmp);
 
      String result = new String(tmp.toByteArray());
      System.out.println(result);
 
      assertElementHasAttribute(result, "/aaaWithPrefix", "xmlns:aaa", "namespace1");
      assertElementHasAttribute(result, "/aaaWithPrefix/bbb", "xmlns:bbb", "namespace2");
      assertElementDoesNotHaveAttribute(result, "/aaaWithPrefix/bbb/aaa", "xmlns:aaa", "namespace1");
      assertElementDoesNotHaveAttribute(result, "/aaaWithPrefix/bbb/aaa/bbb", "xmlns:bbb", "namespace2");
      
      assertElementHasNamespace(result, "/aaaWithPrefix", "namespace1");
      assertElementHasNamespace(result, "/aaaWithPrefix/bbb", "namespace2");
      assertElementHasNamespace(result, "/aaaWithPrefix/bbb/aaa", "namespace1");
      assertElementHasNamespace(result, "/aaaWithPrefix/bbb/aaa/bbb", "namespace2");
   }
}