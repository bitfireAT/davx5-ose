package org.simpleframework.xml.core;

import java.util.concurrent.TimeUnit;

import junit.framework.TestCase;

public class TimerTest extends TestCase {

   public void testTimer() throws Exception {
      Timer timer = new Timer(TimeUnit.MILLISECONDS);
      timer.start();
      System.err.println("Test time");
      timer.stopThenStart("STDERR");
   }
}
