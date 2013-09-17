package org.simpleframework.xml.core;



import java.io.StringWriter;
import java.util.Arrays;

import junit.framework.TestCase;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
public class Test3Test extends TestCase {


   @Root(name="test3")
   public static class Test3 {
      
      @Path(value="elements")
      @ElementListUnion({
         @ElementList(entry="element-a", type=MyElementA.class, inline=true),
         @ElementList(entry="element-b", type=MyElementB.class, inline=true)
      })
      final java.util.ArrayList<MyElement> elements;
      
      public Test3(final MyElement... elements){
         this(new java.util.ArrayList<MyElement>(Arrays.asList(elements)));
      }
      
      //FIXME obviously the constructor matches, so why the exception?
      public Test3(  @Path(value="elements")
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
   
   @Root
   public static class MyElement{
      
   }
   
   public static class MyElementA extends MyElement{
      
   }
   
   public static class MyElementB extends MyElement{
      
   }   

   public void testConstructor() throws Exception{
      Serializer s = new Persister();
      StringWriter sw = new StringWriter();
      s.write(new Test3(new MyElementA(), new MyElementB()), sw);    
      String serializedForm = sw.toString();
      System.out.println(serializedForm);
      System.out.println();
      Test3 o = s.read(Test3.class, serializedForm);
      sw.getBuffer().setLength(0);
      s.write(o, sw);
      System.out.println(sw.toString());
      System.out.println();
   }

}
