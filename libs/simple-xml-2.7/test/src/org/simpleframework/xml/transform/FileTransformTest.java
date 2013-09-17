package org.simpleframework.xml.transform;

import java.io.File;

import org.simpleframework.xml.transform.FileTransform;

import junit.framework.TestCase;

public class FileTransformTest extends TestCase {
   
   public void testFile() throws Exception {
      File file = new File("..");
      FileTransform format = new FileTransform();
      String value = format.write(file);
      File copy = format.read(value);
      
      assertEquals(file, copy);      
   }
}