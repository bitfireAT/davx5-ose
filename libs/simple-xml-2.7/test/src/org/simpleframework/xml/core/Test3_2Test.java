package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.Arrays;

import junit.framework.TestCase;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

public class Test3_2Test extends TestCase {
   
   @Root(name="test3")
   public static class Test3_2 {
      
      final java.util.Collection<MyElement> elements;
      
      @Path(value="elements")
      @ElementListUnion({
         @ElementList(entry="element-a", type=MyElementA.class, inline=true),
         @ElementList(entry="element-b", type=MyElementB.class, inline=true)
      })
      public java.util.ArrayList<MyElement> getElements(){
         return new java.util.ArrayList<MyElement>(this.elements);
      }
      
      public Test3_2(final MyElement... elements){
         this(new java.util.ArrayList<MyElement>(Arrays.asList(elements)));
      }
      
      //FIXME why isn't a get Method without a corresponding set method treated like a field that is declared final
      public Test3_2(   @Path(value="elements")
                  @ElementListUnion({
                     @ElementList(entry="element-a", type=MyElementA.class, inline=true),
                     @ElementList(entry="element-b", type=MyElementB.class, inline=true)
                  })
                  final java.util.ArrayList<MyElement> elements
            ) {
         super();
         this.elements = elements;
      }
   }
   
   @Root(name="test3")
   public static class Test3_3 {
      
      @Path(value="elements")
      @ElementListUnion({
         @ElementList(entry="element-a", type=MyElementA.class, inline=true),
         @ElementList(entry="element-b", type=MyElementB.class, inline=true)
      })
      final java.util.Collection<MyElement> elements;
      
      public Test3_3(final MyElement... elements){
         this(new java.util.ArrayList<MyElement>(Arrays.asList(elements)));
      }
      
      //FIXME why isn't a get Method without a corresponding set method treated like a field that is declared final
      public Test3_3(   @Path(value="elements")
                  @ElementListUnion({
                     @ElementList(entry="element-a", type=MyElementA.class, inline=true),
                     @ElementList(entry="element-b", type=MyElementB.class, inline=true)
                  })
                  final java.util.Collection<MyElement> elements
            ) {
         super();
         this.elements = elements;
      }
   }

   @Root
   public static class MyElement{
      
   }
   
   public static class MyElementA extends MyElement{
      
   }
   
   public static class MyElementB extends MyElement{
      
   }

   public void test32() throws Exception{
      Serializer s = new Persister();
      StringWriter sw = new StringWriter();
      s.write(new Test3_2(new MyElementA(), new MyElementB()), sw);     
      String serializedForm = sw.toString();
      System.out.println(serializedForm);
      System.out.println();
      Test3_2 o = s.read(Test3_2.class, serializedForm);
      sw.getBuffer().setLength(0);
      s.write(o, sw);
      System.out.println(sw.toString());
      System.out.println();
   }
   
   public void test33() throws Exception{
      Serializer s = new Persister();
      StringWriter sw = new StringWriter();
      s.write(new Test3_3(new MyElementA(), new MyElementB()), sw);     
      String serializedForm = sw.toString();
      System.out.println(serializedForm);
      System.out.println();
      Test3_3 o = s.read(Test3_3.class, serializedForm);
      sw.getBuffer().setLength(0);
      s.write(o, sw);
      System.out.println(sw.toString());
      System.out.println();
   }
}
