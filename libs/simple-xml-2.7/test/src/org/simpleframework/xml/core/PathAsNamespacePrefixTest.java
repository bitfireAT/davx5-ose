package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementUnion;

public class PathAsNamespacePrefixTest extends ValidationTestCase {

   @Root
   @Namespace(prefix="p", reference="http://www.x.com/y")
   private static class PathWithPrefix {
      @Path("x/y/p:z")
      @Namespace(reference="http://www.x.com/y")
      @ElementUnion({
         @Element(name="name"),
         @Element(name="login"),
         @Element(name="user")
      })
      private final String id;
      public PathWithPrefix(@Element(name="name") String id){
         this.id = id;
      }
      public String getId(){
         return id;
      }
   }
   
   @Root
   private static class PathWithPrefixOutOfScope {
      @Path("x/y/p:z")
      @Namespace(reference="http://www.x.com/y")
      @ElementUnion({
         @Element(name="name"),
         @Element(name="login"),
         @Element(name="user")
      })
      private final String id;
      public PathWithPrefixOutOfScope(@Element(name="name") String id){
         this.id = id;
      }
      public String getId(){
         return id;
      }
   }
   
   @Default
   @Namespace(prefix="p", reference="http://www.x.com/p")
   private static class BringPrefixInScope {
      private final PathWithPrefixOutOfScope example;
      public BringPrefixInScope(@Element(name="example") PathWithPrefixOutOfScope example){
         this.example = example;
      }
   }
   
   public void testPathWithPrefix() throws Exception{
      Persister persister = new Persister();
      PathWithPrefix example = new PathWithPrefix("tim");
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      String text = writer.toString();
      System.out.println(text);
      PathWithPrefix recovered = persister.read(PathWithPrefix.class, text);
      assertEquals(recovered.getId(), "tim");
      validate(persister, example);
   }
   
   public void testPathWithPrefixOufOfScope() throws Exception{
      Persister persister = new Persister();
      PathWithPrefixOutOfScope example = new PathWithPrefixOutOfScope("tim");
      StringWriter writer = new StringWriter();
      boolean exception = false;
      try {
         persister.write(example, writer);
      }catch(Exception e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("Prefix that is not in scope should be illegal", exception);
   }
   
   public void testBringPrefixInScope() throws Exception{
      Persister persister = new Persister();
      PathWithPrefixOutOfScope example = new PathWithPrefixOutOfScope("tim");
      BringPrefixInScope parent = new BringPrefixInScope(example);
      StringWriter writer = new StringWriter();
      persister.write(parent, writer);
      System.out.println(writer);
      BringPrefixInScope recovered = persister.read(BringPrefixInScope.class, writer.toString());
      assertEquals(recovered.example.getId(), "tim");
      validate(persister, parent);
   }
}
