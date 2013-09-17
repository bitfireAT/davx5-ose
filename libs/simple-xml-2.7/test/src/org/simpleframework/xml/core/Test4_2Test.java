package org.simpleframework.xml.core;

import java.io.StringWriter;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

public class Test4_2Test extends TestCase {

   @Root(name="test4")
   public static class Test4_2 {
      
      @ElementUnion({
            @Element(name="single-elementA", type=MyElementA.class),
            @Element(name="single-elementB", type=MyElementB.class),
            @Element(name="single-element", type=MyElement.class)
      })
      MyElement element;
      
      Test4_2(){
         
      }
      
      public Test4_2(final MyElement element) {
         super();
         this.element = element;
      }
      
   }

   
   @Root
   public static class MyElement{
      
   }
   
   public static class MyElementA extends MyElement{
      
   }
   
   public static class MyElementB extends MyElement{
      
   }
   
   public static class MyElementC extends MyElement{
      
   }

   public void testSerialization() throws Exception{
      Serializer s = new Persister();
      StringWriter sw = new StringWriter();     
            
      String serializedForm =    "<test4>\n" + 
                           "   <single-element class=\"org.simpleframework.xml.core.Test4_2Test$MyElementC\"/>\n" +
                           "</test4>";
      System.out.println(serializedForm);
      System.out.println();
      
      Test4_2 o = s.read(Test4_2.class, serializedForm);
      
      //FIXME read ignores the class statement

      MyElementC ec = (MyElementC) o.element;
      
      sw.getBuffer().setLength(0);
      s.write(new Test4_2(new MyElementC()), sw);
      //FIXME it would be great, if this worked. Actually it works for ElementUnionLists.
      System.out.println(sw.toString());
      System.out.println();

   } 
}
