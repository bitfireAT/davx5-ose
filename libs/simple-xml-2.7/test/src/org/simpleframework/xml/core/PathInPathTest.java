package org.simpleframework.xml.core;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.ValidationTestCase;

public class PathInPathTest extends ValidationTestCase {

   @Default
   public static class PathInPathRoot {
      @Path("a/b[1]/c")
      private final PathInPathEntry d;
      @Path("a/b[2]/c")
      private final PathInPathEntry e;
      public PathInPathRoot(
            @Element(name="d") PathInPathEntry d,
            @Element(name="e") PathInPathEntry e) 
      {
         this.d = d;
         this.e = e;
      }
      public PathInPathEntry getD(){
         return d;
      }
      public PathInPathEntry getE(){
         return d;
      }
   }
   
   @Default
   public static class PathInPathEntry {
      @Path("x/y")
      private final String v;
      @Path("x/z")
      private final String w;
      public PathInPathEntry(
            @Element(name="v") String v,
            @Element(name="w") String w)
      {
         this.v = v;
         this.w = w;
      }
      public String getW(){
         return w;
      }
      public String getV(){
         return v;
      }
   }
   
   public void testPathInPath() throws Exception {
      Persister persister = new Persister();
      PathInPathRoot root = new PathInPathRoot(
            new PathInPathEntry("Value for V", "Value for W"),
            new PathInPathEntry("Another V", "Another W"));
      persister.write(root, System.err);
      validate(root, persister);
   }
}
