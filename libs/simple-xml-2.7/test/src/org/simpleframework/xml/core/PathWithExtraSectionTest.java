package org.simpleframework.xml.core;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.ValidationTestCase;

public class PathWithExtraSectionTest extends ValidationTestCase {

   private static final String SOURCE =
   "<pathSectionIndexExample>\n"+
   "   <contact-details>\n"+
   "      <phone>\n"+
   "         <mobile>67890</mobile>\n"+
   "      </phone>\n"+
   "   </contact-details>\n"+
   "   <contact-details>\n"+
   "      <phone>\n"+
   "         <home>12345</home>\n"+
   "      </phone>\n"+
   "   </contact-details>\n"+
   "   <contact-details>\n"+
   "      <phone>\n"+
   "         <office>23523</office>\n"+
   "      </phone>\n"+
   "   </contact-details>\n"+   
   "</pathSectionIndexExample>\n";

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
   
   public void testExtraSection() throws Exception {
      Persister persister = new Persister();
      boolean exception = false;
      
      try {
         persister.read(PathSectionIndexExample.class, SOURCE);
      }catch(Exception e) {
         e.printStackTrace();
         exception = true;
      }
      assertTrue("There should be an exception for unmatched section", exception);     
   }
   
   public void testExtraSectionWithNonStrict() throws Exception {
      Persister persister = new Persister();
      PathSectionIndexExample example = persister.read(PathSectionIndexExample.class, SOURCE, false);
      assertEquals(example.home, "12345");
      assertEquals(example.mobile, "67890");            
   }
}
