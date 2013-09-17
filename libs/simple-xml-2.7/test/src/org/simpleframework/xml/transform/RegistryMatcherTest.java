package org.simpleframework.xml.transform;

import java.io.StringWriter;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;

public class RegistryMatcherTest extends ValidationTestCase {
   
   public static class A {
      private String value;
      public A(String value){
         this.value = value;
      }
      public String getValue(){
         return value;
      }
   }
   public static class B {
      private String value;
      public B(String value){
         this.value = value;
      }
      public String getValue(){
         return value;
      }
   }
   public static class C {
      private String value;
      public C(String value){
         this.value = value;
      }
      public String getValue(){
         return value;
      }
   }
   public static class ATransform implements Transform<A> {
      public A read(String value) throws Exception {
         return new A(value);
      }
      public String write(A value) throws Exception {
         return value.getValue();
      }    
   }
   public static class BTransform implements Transform<B> {
      public B read(String value) throws Exception {
         return new B(value);
      }
      public String write(B value) throws Exception {
         return value.getValue();
      }    
   }
   public static class CTransform implements Transform<C> {
      public C read(String value) throws Exception {
         return new C(value);
      }
      public String write(C value) throws Exception {
         return value.getValue();
      }    
   }
   @Root
   private static class Example {
      @Element
      private final A a;
      @Attribute
      private final B b;
      @Attribute
      private final C c;
      public Example(A a, B b, C c) {
         this.a = a;
         this.b = b;
         this.c = c;
      }
      public A getA() {
         return a;
      }
      public B getB() {
         return b;
      }
      public C getC() {
         return c;
      }
   }
   
   public void testMatcher() throws Exception {
      RegistryMatcher matcher = new RegistryMatcher();
      Transform<C> transform = new CTransform();
      matcher.bind(A.class, ATransform.class);
      matcher.bind(B.class, BTransform.class);
      matcher.bind(C.class, transform);
      Transform<A> a = matcher.match(A.class);
      Transform<B> b = matcher.match(B.class);
      Transform<C> c = matcher.match(C.class);
      A ia = a.read("A");
      B ib = b.read("B");
      C ic = c.read("C");
      assertEquals(ia.getValue(), "A");
      assertEquals(ib.getValue(), "B");
      assertEquals(ic.getValue(), "C");
      Persister persister = new Persister(matcher);
      Example example = new Example(ia, ib, ic);
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      String text = writer.toString();
      assertElementExists(text, "/example");
      assertElementExists(text, "/example/a");
      assertElementHasValue(text, "/example/a", "A");
      assertElementHasAttribute(text, "/example", "b", "B");
      assertElementHasAttribute(text, "/example", "c", "C");
   }
   

}
