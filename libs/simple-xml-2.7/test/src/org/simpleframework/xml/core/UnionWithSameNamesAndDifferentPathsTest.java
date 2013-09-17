package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class UnionWithSameNamesAndDifferentPathsTest extends ValidationTestCase {

   @Root
   private static class Example {
      @Path("path[1]")
      @Namespace(prefix="x", reference="http://www.xml.com/ns")
      @ElementUnion({
         @Element(name="a"),
         @Element(name="b"),
         @Element(name="c")
      })
      private String x;
      
      @Path("path[2]")
      @Namespace(prefix="x", reference="http://www.xml.com/ns")
      @ElementUnion({
         @Element(name="a"),
         @Element(name="b"),
         @Element(name="c")
      })
      private String y;
      
      public Example(
            @Path("path[1]") @Element(name="b") String x, 
            @Path("path[2]") @Element(name="b") String y)
      {
         this.y = y;
         this.x = x;
      }
   }
   
   @Root
   private static class AmbiguousConstructorParameterExample {
      @Path("path[1]")
      @Namespace(prefix="x", reference="http://www.xml.com/ns")
      @ElementUnion({
         @Element(name="a"),
         @Element(name="b"),
         @Element(name="c")
      })
      private String x;
      
      @Path("path[2]")
      @Namespace(prefix="x", reference="http://www.xml.com/ns")
      @ElementUnion({
         @Element(name="a"),
         @Element(name="b"),
         @Element(name="c")
      })
      private String y;
      
      public AmbiguousConstructorParameterExample(
            @Element(name="b") String ambiguous, 
            @Path("path[2]") @Element(name="b") String y)
      {
         this.y = y;
         this.x = ambiguous;
      }
   }
   
   public void testNamespaceWithUnion() throws Exception{
      Persister persister = new Persister();
      Example example = new Example("X", "Y");
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      String text = writer.toString();
      Example deserialized = persister.read(Example.class, text);
      assertEquals(deserialized.x, "X");
      assertEquals(deserialized.y, "Y");
      validate(persister, example);
      assertElementExists(text, "/example/path[1]/a");
      assertElementHasValue(text, "/example/path[1]/a", "X");
      assertElementHasNamespace(text, "/example/path[1]/a", "http://www.xml.com/ns");
      assertElementExists(text, "/example/path[2]/a");
      assertElementHasValue(text, "/example/path[2]/a", "Y");
      assertElementHasNamespace(text, "/example/path[2]/a", "http://www.xml.com/ns");
   }
   
   public void testErrorInParametersExample() throws Exception{
      boolean failure = false;
      try {
         Persister persister = new Persister();
         AmbiguousConstructorParameterExample example = new AmbiguousConstructorParameterExample("X", "Y");
         persister.write(example, System.out);
      } catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Ambiguous parameter should cause a failure", failure);
   }
}
