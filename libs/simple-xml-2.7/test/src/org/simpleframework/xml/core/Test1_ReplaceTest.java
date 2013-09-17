package org.simpleframework.xml.core;


import java.io.StringWriter;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

public class Test1_ReplaceTest extends TestCase {

   @Root(name="test1")
   public static class Test1 {
      
      @Element(name="type")
      final String type;
      
      protected Test1(@Element(name="type") final String type) {
         super();
         this.type = type;
      }
      
      @Replace
      public Test1 replace(){
         return new Test1(this.type);
      }
      
      @Resolve
      public Test1 resolve(){
         if(this.type.equals("A")){
            return new Test1A(null);
         }
         else if(this.type.equals("B")){
            return new Test1B();
         }
         else{
            return this;
         }
      }
   }
   
   public static class Test1A extends Test1{    
      
      public Test1A(final String ignored){
         super("A");
      }
      
   }
   
   public static class Test1B extends Test1{    
      
      public Test1B(){
         super("B");
      }
      
   }

   public void testReplace() throws Exception{
      Serializer s = new Persister();
      StringWriter sw = new StringWriter();
      s.write(new Test1A(null), sw);      
      String serializedForm = sw.toString();
      System.out.println(serializedForm);
      System.out.println();
      Test1 o = s.read(Test1.class, serializedForm);
      
      sw.getBuffer().setLength(0);
      
      //Unnecessary constructor exception a write, note that this happens with the default constructor only (see above)
     // s.write(new Test1B(), sw);    
      serializedForm = sw.toString();
      System.out.println(serializedForm);
     // o = s.read(Test1.class, serializedForm);
   }
   
}
