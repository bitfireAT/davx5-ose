package org.simpleframework.xml.transform;

import java.net.URL;

import org.simpleframework.xml.transform.URLTransform;

import junit.framework.TestCase;

public class URLTransformTest extends TestCase {
   
   public void testURL() throws Exception {
      URL file = new URL("http://www.google.com/");
      URLTransform format = new URLTransform();
      String value = format.write(file);
      URL copy = format.read(value);
      
      assertEquals(file, copy);      
   }
}