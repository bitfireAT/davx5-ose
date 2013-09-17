package org.simpleframework.xml.core;


import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

public class Test2_ReplaceTest extends TestCase {

   @Root(name="test2")
   public static class Test2 {
      
      @Element(name="type")
      final String type;
      
      @Element(name="element", required=false)
      final Test2 element;
      
      @ElementList(name="elements")
      final ArrayList<Test2> elements; 
      
      protected Test2(@Element(name="type") final String type
            , @Element(name="element") final Test2 element
            , @ElementList(name="elements") final ArrayList<Test2> elements) {
         super();
         this.type = type;
         this.element = element;
         this.elements = elements;
      }
      
      @Replace
      public Test2 replace(){
         return new Test2(this.type, this.element, this.elements);
      }
      
      @Resolve
      public Test2 resolve(){
         if(this.type.equals("A")){
            return new Test2A(this.element, this.elements.toArray(new Test2[0]));
         }
         else{
            return this;
         }
      }
   }
   
   
   public static class Test2A extends Test2{    
      
      public Test2A(final Test2 element, final Test2... elements){
         super("A", element, new ArrayList<Test2>(Arrays.asList(elements)));
      }
      
   }

   public void testReplace() throws Exception{
      Serializer s = new Persister();
      StringWriter sw = new StringWriter();
      Test2A element = new Test2A(null);
      s.write(new Test2A(element, element), sw);      
      String serializedForm = sw.toString();
      System.out.println(serializedForm);
      System.out.println();
      
      //"element" is serialzed differently depending on whether it is serialized as a part of a list or not.
      //Apparently the replace method is not applied when "element" is serialized as part of a list.
      //Particularly the replace method is only called once, when debugging the serialization. However it should be called twice.
      
      //Test2 o = s.read(Test2.class, serializedForm);
      
      sw.getBuffer().setLength(0);
   }
}
