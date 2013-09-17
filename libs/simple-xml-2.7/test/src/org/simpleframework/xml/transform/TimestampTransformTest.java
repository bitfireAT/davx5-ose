package org.simpleframework.xml.transform;

import java.sql.Timestamp;
import java.util.Date;

import junit.framework.TestCase;

import org.simpleframework.xml.transform.DateTransform;

public class TimestampTransformTest extends TestCase {
   
   public void testTimestamp() throws Exception {
      long now = System.currentTimeMillis();
      Timestamp date = new Timestamp(now);
      DateTransform format = new DateTransform(Timestamp.class);
      String value = format.write(date);
      Date copy = format.read(value);
      
      assertEquals(date, copy);  
      assertEquals(copy.getTime(), now);
      assertTrue(copy instanceof Timestamp);
   }
}