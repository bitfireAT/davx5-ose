package org.simpleframework.xml.transform;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import junit.framework.TestCase;

public class DateFormatterTest extends TestCase {
   
   private static final String FORMAT = "yyyy-MM-dd HH:mm:ss.S z";
   
   private static final int CONCURRENCY = 10;
   
   private static final int COUNT = 100;
   
   public void testFormatter() throws Exception {
      DateFormatter formatter = new DateFormatter(FORMAT);
      Date date = new Date();
      
      String value = formatter.write(date);
      Date copy = formatter.read(value);
      
      assertEquals(date, copy);      
   }
   
   public void testPerformance() throws Exception {      
      CountDownLatch simpleDateFormatGate = new CountDownLatch(CONCURRENCY);
      CountDownLatch simpleDateFormatFinisher = new CountDownLatch(CONCURRENCY);
      AtomicLong simpleDateFormatCount = new AtomicLong();
      
      for(int i = 0; i < CONCURRENCY; i++) {
         new Thread(new SimpleDateFormatTask(simpleDateFormatFinisher, simpleDateFormatGate, simpleDateFormatCount, FORMAT)).start();
      }
      simpleDateFormatFinisher.await();
      
      CountDownLatch synchronizedGate = new CountDownLatch(CONCURRENCY);
      CountDownLatch synchronizedFinisher = new CountDownLatch(CONCURRENCY);
      AtomicLong synchronizedCount = new AtomicLong();
      SimpleDateFormat format = new SimpleDateFormat(FORMAT);
      
      for(int i = 0; i < CONCURRENCY; i++) {
         new Thread(new SynchronizedTask(synchronizedFinisher, synchronizedGate, synchronizedCount, format)).start();
      }           
      synchronizedFinisher.await();
      
      CountDownLatch formatterGate = new CountDownLatch(CONCURRENCY);
      CountDownLatch formatterFinisher = new CountDownLatch(CONCURRENCY);
      AtomicLong formatterCount = new AtomicLong();
      DateFormatter formatter = new DateFormatter(FORMAT, CONCURRENCY);
      
      for(int i = 0; i < CONCURRENCY; i++) {
         new Thread(new FormatterTask(formatterFinisher, formatterGate, formatterCount, formatter)).start();
      }
      formatterFinisher.await();
      
      System.err.printf("pool: %s, new: %s, synchronized: %s", formatterCount.get(),  simpleDateFormatCount.get(), synchronizedCount.get());
      
      //assertTrue(formatterCount.get() < simpleDateFormatCount.get());
      //assertTrue(formatterCount.get() < synchronizedCount.get()); // Synchronized is faster?
   }
   
   private class FormatterTask implements Runnable {
      
      private DateFormatter formatter;
      
      private CountDownLatch gate;
      
      private CountDownLatch main;
      
      private AtomicLong count;

      public FormatterTask(CountDownLatch main, CountDownLatch gate, AtomicLong count, DateFormatter formatter) {         
         this.formatter = formatter;
         this.count = count;
         this.gate = gate;
         this.main = main;
      }
      
      public void run() {
         long start = System.currentTimeMillis();
         
         try {
            gate.countDown();
            gate.await();
            
            Date date = new Date();
            
            for(int i = 0; i < COUNT; i++) {
               String value = formatter.write(date);
               Date copy = formatter.read(value);

               assertEquals(date, copy);
            }
         }catch(Exception e) {
            assertTrue(false);
         } finally {
            count.getAndAdd(System.currentTimeMillis() - start);
            main.countDown();
         }
      }
   }
   
   private class SimpleDateFormatTask implements Runnable {
      
      private CountDownLatch gate;
      
      private CountDownLatch main;
      
      private AtomicLong count;
      
      private String format;
      
      public SimpleDateFormatTask(CountDownLatch main, CountDownLatch gate, AtomicLong count, String format) {
         this.format = format;
         this.count = count;
         this.gate = gate;
         this.main = main;
      }
      
      public void run() {
         long start = System.currentTimeMillis();
         
         try {
            gate.countDown();
            gate.await();
             
            Date date = new Date();
            
            for(int i = 0; i < COUNT; i++) {
               String value = new SimpleDateFormat(format).format(date);
               Date copy = new SimpleDateFormat(format).parse(value);
               
               assertEquals(date, copy);
            }
         }catch(Exception e) {
            assertTrue(false);
         } finally {
            count.getAndAdd(System.currentTimeMillis() - start);
            main.countDown();
         }
      }
   }   
   
   private class SynchronizedTask implements Runnable {
      
      private SimpleDateFormat format;
      
      private CountDownLatch gate;
      
      private CountDownLatch main;
      
      private AtomicLong count;

      public SynchronizedTask(CountDownLatch main, CountDownLatch gate, AtomicLong count, SimpleDateFormat format) {
         this.format = format;
         this.count = count;
         this.gate = gate;
         this.main = main;
      }
      
      public void run() {
         long start = System.currentTimeMillis();
         
         try {
            gate.countDown();
            gate.await();
             
            Date date = new Date();
            
            for(int i = 0; i < COUNT; i++) {
               synchronized(format) {
                  String value = format.format(date);
                  Date copy = format.parse(value);
               
                  assertEquals(date, copy);
               }
            }
         }catch(Exception e) {
            assertTrue(false);
         } finally {
            count.getAndAdd(System.currentTimeMillis() - start);
            main.countDown();
         }
      }
   }
}
