package org.simpleframework.xml.core;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementUnion;

public class ElementWithExplicitTypeTest extends ValidationTestCase {
   
   @Root
   private static class UnionExample {
      @ElementUnion({
         @Element(name="s", type=String.class),
         @Element(name="i", type=Integer.class),
         @Element(name="d", type=Double.class)
      })
      private Object value;      
   }
   
   @Root
   private static class Example{
      @Element(type=String.class)
      private final Object string;
      @Element(type=Integer.class)
      private final Object integer;
      @Element(type=Boolean.class)
      private final Object bool;
      public Example(
         @Element(name="string") Object string, 
         @Element(name="integer") Object integer, 
         @Element(name="bool") Object bool) 
      {
         this.string = string;
         this.integer = integer;
         this.bool = bool;
      }
   }
   
   @Root
   private static class A{}
   private static class B extends A {}
   private static class C extends B {}
   
   @Root
   private static class HeirarchyExample{      
      @Element(type=A.class)
      private final Object a;
      @Element(type=B.class)
      private final Object b;
      @Element(type=C.class)
      private final Object c;
      public HeirarchyExample(
         @Element(name="a") Object a, 
         @Element(name="b") Object b, 
         @Element(name="c") Object c)
      {
         this.a = a;
         this.b = b;
         this.c = c;
      }
      
   }
   
   public void testExplicitType() throws Exception{
      Persister persister = new Persister();
      Example example = new Example("str", 10, true);
      persister.write(example, System.out);
      validate(persister, example);
   }
   
   public void testExplicitUnionType() throws Exception{
      Persister persister = new Persister();
      UnionExample example = new UnionExample();
      example.value = "str";
      persister.write(example, System.out);
      validate(persister, example);
   }
   
   public void testExplicitHierarchyType() throws Exception{
      Persister persister = new Persister();
      HeirarchyExample example = new HeirarchyExample(new B(), new C(), new C());      
      persister.write(example, System.out);
      validate(persister, example);
   }   
   
   public void testExplicitIncorrectHierarchyType() throws Exception{
      Persister persister = new Persister();
      HeirarchyExample example = new HeirarchyExample(new B(), new A(), new A());      
      persister.write(example, System.out);
      validate(persister, example);
   }
}

