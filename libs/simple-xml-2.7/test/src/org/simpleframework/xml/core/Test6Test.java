package org.simpleframework.xml.core;

import junit.framework.TestCase;

import java.io.StringWriter;
import java.util.Arrays;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

public class Test6Test extends TestCase {

   @Root(name="test6")
   public static class Test6 {
      
      @ElementList(name="elements", entry="element", type=MyElement.class)
      final java.util.ArrayList<MyElement> elements;
      boolean listConstructorUsed = true;
      
      /*FIXME why does adding the default constructor spoil the serialization? Without the default constructor everything is ok*/
   public Test6(){
       this(new java.util.ArrayList<MyElement>());
       this.listConstructorUsed = false;
    }
      
      public Test6(final MyElement... elements){
         this(new java.util.ArrayList<MyElement>(Arrays.asList(elements)));
         this.listConstructorUsed = false;
      }
      
      public Test6(@ElementList(name="elements", entry="element", type=MyElement.class)
                  final java.util.ArrayList<MyElement> elements
            ) {
         super();
         this.elements = elements;
      }      
      
      public boolean isListConstructorUsed() {
         return listConstructorUsed;
      }
   }
   
   
   @Root
   public static class MyElement{
      
   }
   
   public static class MyElementA extends MyElement{
      
   }
   
   public static class MyElementB extends MyElement{
      
   }

   public void testSerialize() throws Exception{
      Serializer s = new Persister();
      StringWriter sw = new StringWriter();
      s.write(new Test6(new MyElementA(), new MyElementB()), sw);    
      String serializedForm = sw.toString();
      System.out.println(serializedForm);
      System.out.println();
      Test6 o = s.read(Test6.class, serializedForm);
      assertTrue(o.isListConstructorUsed());
      sw.getBuffer().setLength(0);
      s.write(o, sw);
      System.out.println(sw.toString());
      System.out.println();
   }
}
