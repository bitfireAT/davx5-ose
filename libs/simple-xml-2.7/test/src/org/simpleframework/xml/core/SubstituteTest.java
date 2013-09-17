package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.core.Persist;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.core.Replace;
import org.simpleframework.xml.core.Resolve;
import org.simpleframework.xml.core.Validate;

import org.simpleframework.xml.ValidationTestCase;

public class SubstituteTest extends ValidationTestCase {

   private static final String REPLACE_SOURCE =
   "<?xml version=\"1.0\"?>\n"+
   "<substituteExample>\n"+
   "   <substitute class='org.simpleframework.xml.core.SubstituteTest$SimpleSubstitute'>some example text</substitute>  \n\r"+
   "</substituteExample>";
   
   private static final String RESOLVE_SOURCE =
   "<?xml version=\"1.0\"?>\n"+
   "<substituteExample>\n"+
   "   <substitute class='org.simpleframework.xml.core.SubstituteTest$YetAnotherSubstitute'>some example text</substitute>  \n\r"+
   "</substituteExample>";

   @Root
   private static class SubstituteExample {

      @Element   
      public Substitute substitute;
      
      public SubstituteExample() {
         super();         
      }
      
      public SubstituteExample(Substitute substitute) {
         this.substitute = substitute;
      }
   }

   @Root
   private static class Substitute {

      @Text
      public String text;
   }

   private static class SimpleSubstitute extends Substitute {
      
      @Replace
      public Substitute replace() {
         return new OtherSubstitute("this is the other substitute", text);
      }
      
      @Persist
      public void persist() {
         throw new IllegalStateException("Simple substitute should never be written only read");
      }
   }
   
   private static class OtherSubstitute extends Substitute {
      
      @Attribute
      public String name;
      
      public OtherSubstitute() {
         super();
      }
      
      public OtherSubstitute(String name, String text) {
         this.text = text;
         this.name = name;
      }
   }
   
   private static class YetAnotherSubstitute extends Substitute {
   
      public YetAnotherSubstitute() {
         super();
      }
      
      @Validate
      public void validate() {
         return;
      }
      
      @Resolve
      public Substitute resolve() {
         return new LargeSubstitute(text, "John Doe", "Sesame Street", "Metropilis");
      }
   }
   
   private static class LargeSubstitute extends Substitute {
      
      @Attribute
      private String name;
      
      @Attribute 
      private String street;
      
      @Attribute
      private String city;
      
      public LargeSubstitute() {
         super();
      }
      
      public LargeSubstitute(String text, String name, String street, String city) {
         this.name = name;
         this.street = street;
         this.city = city;
         this.text = text;
      }
   }
        
   private Persister serializer;

   public void setUp() {
      serializer = new Persister();
   }
   
   public void testReplace() throws Exception {    
      SubstituteExample example = serializer.read(SubstituteExample.class, REPLACE_SOURCE);
      
      assertEquals(example.substitute.getClass(), SimpleSubstitute.class);
      assertEquals(example.substitute.text, "some example text");
      
      validate(example, serializer);
      
      StringWriter out = new StringWriter();
      serializer.write(example, out);
      String text = out.toString();     
      
      example = serializer.read(SubstituteExample.class, text);
      
      assertEquals(example.substitute.getClass(), OtherSubstitute.class);
      assertEquals(example.substitute.text, "some example text");
           
      validate(example, serializer);
   }
   
   
   public void testResolve() throws Exception {    
      SubstituteExample example = serializer.read(SubstituteExample.class, RESOLVE_SOURCE);
      
      assertEquals(example.substitute.getClass(), LargeSubstitute.class);
      assertEquals(example.substitute.text, "some example text");
      
      validate(example, serializer);
      
      StringWriter out = new StringWriter();
      serializer.write(example, out);
      String text = out.toString();      
      
      example = serializer.read(SubstituteExample.class, text);
      
      assertEquals(example.substitute.getClass(), LargeSubstitute.class);
      assertEquals(example.substitute.text, "some example text");
           
      validate(example, serializer);
   }
}
