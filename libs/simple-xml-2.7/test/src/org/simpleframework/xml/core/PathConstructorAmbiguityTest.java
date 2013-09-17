package org.simpleframework.xml.core;

import java.io.StringWriter;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

public class PathConstructorAmbiguityTest extends TestCase {

   @Root(name="test1")
   public static class Test1 {
      
      @Attribute(name="iri")
      final String identifier;
      
      @Path(value="resource")
      @Attribute(name="iri")
      final String identifier1;
      
      public Test1(
            @Attribute(name="iri")  String identifier,
            @Path(value="resource") @Attribute(name="iri") String identifier1)
      {
         this.identifier = identifier;
         this.identifier1 = identifier1;
      }   
   }

      public void testParameters() throws Exception{
         Serializer s = new Persister();
         StringWriter sw = new StringWriter();
         s.write(new Test1("a", "b"), sw);      
         String serializedForm = sw.toString();
         System.out.println(serializedForm);
         Test1 o = s.read(Test1.class, serializedForm);
      }
}
