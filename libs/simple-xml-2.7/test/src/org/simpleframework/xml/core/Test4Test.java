package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.Arrays;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
public class Test4Test extends TestCase {


   @Root(name="test4")
   public static class Test4 {
      
      @ElementUnion({
            @Element(name="single-elementA", type=MyElementA.class),
            @Element(name="single-elementB", type=MyElementB.class),
            @Element(name="single-element", type=MyElement.class)
      })
      MyElement element;
      
      java.util.ArrayList<MyElement> elements;
      
      @Path(value="elements")
      @ElementListUnion({
         @ElementList(entry="elementA", type=MyElementA.class, inline=true),
         @ElementList(entry="elementB", type=MyElementB.class, inline=true),
         @ElementList(entry="element", type=MyElement.class, inline=true)
      })
      java.util.ArrayList<MyElement> getElements(){
         return this.elements;
      }

      @Path(value="elements")
      @ElementListUnion({
         @ElementList(entry="elementA", type=MyElementA.class, inline=true),
         @ElementList(entry="elementB", type=MyElementB.class, inline=true),
         @ElementList(entry="element", type=MyElement.class, inline=true)
      })
      void setElements(final java.util.ArrayList<MyElement> elements){
         this.elements = elements;
      }
      
      Test4(){
         
      }
      
      public Test4(final MyElement element, final MyElement... elements){
         this(element, new java.util.ArrayList<MyElement>(Arrays.asList(elements)));
      }
      
      public Test4(final MyElement element, final java.util.ArrayList<MyElement> elements) {
         super();
         this.element = element;
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
   
   public static class MyElementC extends MyElement{
      
   }
   public void testSerialization() throws Exception{    
      Serializer s = new Persister();
      StringWriter sw = new StringWriter();
      s.write(new Test4(new MyElement(), new MyElementA(), new MyElementB()), sw);     
      String serializedForm = sw.toString();
      System.out.println(serializedForm);
      System.out.println();
      Test4 o = s.read(Test4.class, serializedForm);
      sw.getBuffer().setLength(0);
      s.write(o, sw);
      System.out.println(sw.toString());
      System.out.println();
      sw.getBuffer().setLength(0);
            
      serializedForm =  "<test4>\n" + 
                     "   <single-element class=\"org.simpleframework.xml.core.Test4Test$MyElementC\"/>\n" + 
                     "   <elements>\n" + 
                     "      <elementA/>\n" + 
                     "      <elementB/>\n" + 
                     "   </elements>\n" + 
                     "</test4>";
      //FIXME read ignores the class statement
      System.out.println(serializedForm);
      System.out.println();
      o = s.read(Test4.class, serializedForm);
      sw.getBuffer().setLength(0);
      s.write(o, sw);
      System.out.println(sw.toString());
      System.out.println();
      sw.getBuffer().setLength(0);
      
      serializedForm =  "<test4>\n" + 
            "   <single-element/>\n" + 
            "   <elements>\n" + 
            "      <element class=\"org.simpleframework.xml.core.Test4Test$MyElementC\"/>\n" + 
            "      <elementB/>\n" + 
            "   </elements>\n" + 
            "</test4>";
      //FIXME read uses the class statement and deserializes as expected
      System.out.println(serializedForm);
      System.out.println();
      o = s.read(Test4.class, serializedForm);
      sw.getBuffer().setLength(0);
      s.write(o, sw);
      System.out.println(sw.toString());
      System.out.println();
      sw.getBuffer().setLength(0);
   }
}
