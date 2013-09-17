package org.simpleframework.xml.core;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.TimeUnit;

public class Timer {
   
   private static ThreadMXBean BEAN = ManagementFactory.getThreadMXBean();
   
   private TimeUnit unit;
   private long startTime;
   private boolean started;
   private long id;
   public Timer() {
      this(TimeUnit.MICROSECONDS);
   }
   public Timer(TimeUnit unit) {
      this.id = Thread.currentThread().getId(); 
      this.unit = unit;
   }
   public void start() {
      startTime =  System.nanoTime();//BEAN.getThreadUserTime(id);
      started = true;
   }
   public long stop() {
      if(!started) {
         throw new IllegalStateException("Timer has not started");
      }
      long nanoSeconds = ( System.nanoTime() - startTime);
      return unit.convert(nanoSeconds, TimeUnit.NANOSECONDS);
   }
   public long stopThenStart(){
      long time = stop();
      start();
      return time;
   }
   public long stop(String msg){
      long time = stop();
      System.err.println(msg + " time="+ time+" "+unit);
      return time;
   }
   public long stopThenStart(String msg) {
      long time = stop(msg);
      start();
      return time;
   }
}
