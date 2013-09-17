package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.ValidationTestCase;

public class PathSectionIndexTest extends ValidationTestCase {
   
   @Default
   @SuppressWarnings("all")
   private static class PathSectionIndexExample {
      
      @Path("contact-details[2]/phone")
      private String home;
      
      @Path("contact-details[1]/phone")
      private String mobile;
      
      public PathSectionIndexExample(
         @Element(name="home") String home,
         @Element(name="mobile") String mobile) {
            this.home = home;
            this.mobile = mobile;
         }
   }
   
   public void testSectionIndex() throws Exception {
      Persister persister = new Persister();
      PathSectionIndexExample example = new PathSectionIndexExample("12345", "67890");
      StringWriter writer = new StringWriter();      
      persister.write(example, System.err);
      persister.write(example, writer);
      PathSectionIndexExample recovered= persister.read(PathSectionIndexExample.class, writer.toString());
      assertEquals(recovered.home, example.home);
      assertEquals(recovered.mobile, example.mobile);
      assertElementExists(writer.toString(), "/pathSectionIndexExample/contact-details[1]/phone/mobile");
      assertElementExists(writer.toString(), "/pathSectionIndexExample/contact-details[2]/phone/home");
      validate(example, persister);
      
   }

}
