package org.simpleframework.xml.core;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;

public class PathWithTextInAPathTest extends ValidationTestCase {

   @Root
   public static class InvalidTextWithElement {
      @Path("a/b")
      @Text
      private String a;
      
      @Path("a/b")
      @Element(name="c")
      private String b;
      
      public InvalidTextWithElement(
         @Text String a,
         @Element(name="c") String b) {
            this.a = a;
            this.b = b;
      }
   }
   
   @Root
   public static class InvalidTextWithCrossingPath {
      @Path("a/b")
      @Text
      private String a;
      
      @Path("a/b/c")
      @Text
      private String b;
      
      public InvalidTextWithCrossingPath(
         @Path("a/b") @Text String a,
         @Path("a/b/c") @Text String b) {
            this.a = a;
            this.b = b;
      }
   }
   
   public void testInvalidText() throws Exception {
      Persister persister = new Persister();
      InvalidTextWithElement example = new InvalidTextWithElement("a", "b");
      boolean failure = false;
      try {
         persister.write(example, System.out);
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Text annotation can not exist with elements in same path", failure);
   }
   
   public void testInvalidTextWithCrossingPath() throws Exception {
      Persister persister = new Persister();
      InvalidTextWithCrossingPath example = new InvalidTextWithCrossingPath("a", "b");
      boolean failure = false;
      try {
         persister.write(example, System.out);
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Text annotation can not exist with crossing path", failure);
   }
}
