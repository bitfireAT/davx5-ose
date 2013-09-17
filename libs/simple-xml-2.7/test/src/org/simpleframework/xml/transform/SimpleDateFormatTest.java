package org.simpleframework.xml.transform;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import junit.framework.TestCase;

public class SimpleDateFormatTest extends TestCase {
   
   public void testISO8601() throws ParseException {
      SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
      System.err.println(format.parse("2007-11-02T14:46:03+0100"));
      SimpleDateFormat format2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
      System.err.println(format2.parse("2007-11-02T14:46:03"));
   }

}
