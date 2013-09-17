package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.ValidationTestCase;

public class UnionWithTypeOverridesTest extends ValidationTestCase {

   private static final String SINGLE_ELEMENT_WITH_OVERRIDE_C =    
   "<test4>\n" + 
   "   <single-element class=\"org.simpleframework.xml.core.UnionWithTypeOverridesTest$MyElementC\"/>\n" +
   "</test4>";
   
   private static final String SINGLE_ELEMENT_B =    
   "<test4>\n" + 
   "   <single-elementB/>\n" +
   "</test4>";
   
   private static final String SINGLE_ELEMENT_A =    
   "<test4>\n" + 
   "   <single-elementA/>\n" +
   "</test4>";
   
   @Root(name="test4")
   public static class OverrideTypeExample {
      
      @ElementUnion({
            @Element(name="single-elementA", type=MyElementA.class),
            @Element(name="single-elementB", type=MyElementB.class),
            @Element(name="single-element", type=MyElement.class)
      })
      MyElement element;
      
      public OverrideTypeExample(){
         super();
      }
      
      public OverrideTypeExample(MyElement element) {
         this.element = element;
      }
   }

   
   @Root
   public static class MyElement{}
   public static class MyElementA extends MyElement{}
   public static class MyElementB extends MyElement{}
   public static class MyElementC extends MyElement{}

   public void testMyElementA() throws Exception{
      Serializer persister = new Persister();
      OverrideTypeExample example = persister.read(OverrideTypeExample.class, SINGLE_ELEMENT_A);
            
      assertEquals(example.element.getClass(), MyElementA.class);
      
      StringWriter writer = new StringWriter();  
      persister.write(example, writer);
      persister.write(example, System.out);
      String text = writer.toString();
      
      assertElementExists(text, "/test4/single-elementA");
      assertElementDoesNotHaveAttribute(text, "/test4/single-elementA", "class", "org.simpleframework.xml.core.UnionWithTypeOverridesTest$MyElementA");
   } 
   
   public void testMyElementB() throws Exception{
      Serializer persister = new Persister();
      OverrideTypeExample example = persister.read(OverrideTypeExample.class, SINGLE_ELEMENT_B);
            
      assertEquals(example.element.getClass(), MyElementB.class);
      
      StringWriter writer = new StringWriter();  
      persister.write(example, writer);
      persister.write(example, System.out);
      String text = writer.toString();
      
      assertElementExists(text, "/test4/single-elementB");
      assertElementDoesNotHaveAttribute(text, "/test4/single-elementB", "class", "org.simpleframework.xml.core.UnionWithTypeOverridesTest$MyElementB");
   } 
   
   public void testMyElementWithovOverrideC() throws Exception{
      Serializer persister = new Persister();
      OverrideTypeExample example = persister.read(OverrideTypeExample.class, SINGLE_ELEMENT_WITH_OVERRIDE_C);
            
      assertEquals(example.element.getClass(), MyElementC.class);
      
      StringWriter writer = new StringWriter();  
      persister.write(example, writer);
      persister.write(example, System.out);
      String text = writer.toString();
      
      assertElementExists(text, "/test4/single-element");
      assertElementHasAttribute(text, "/test4/single-element", "class", "org.simpleframework.xml.core.UnionWithTypeOverridesTest$MyElementC");
   } 
}
