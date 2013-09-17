package org.simpleframework.xml.core;

import junit.framework.TestCase;
import java.io.StringWriter;
import java.util.Arrays;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

public class Test5Test extends TestCase {

   @Root(name="test5")
   public static class Test5 {
      
      @ElementUnion({
            @Element(name="elementA", type=MyElementA.class),
            @Element(name="elementB", type=MyElementB.class),
            @Element(name="element", type=MyElement.class)
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
      
      Test5(){
         
      }
      
      public Test5(final MyElement element, final MyElement... elements){
         this(element, new java.util.ArrayList<MyElement>(Arrays.asList(elements)));
      }
      
      public Test5(final MyElement element, final java.util.ArrayList<MyElement> elements) {
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
   
   public void testSerialize() throws Exception{
      Serializer s = new Persister();
      StringWriter sw = new StringWriter();
      //FIXME serialization is ok
      s.write(new Test5(new MyElementA(), new MyElementA(), new MyElementB()), sw);    
      String serializedForm = sw.toString();
      System.out.println(serializedForm);
      System.out.println();
      //FIXME but no idea what is happening
      Test5 o = s.read(Test5.class, serializedForm);
      sw.getBuffer().setLength(0);
      s.write(o, sw);
      System.out.println(sw.toString());
      System.out.println();
      sw.getBuffer().setLength(0);
   }
}
