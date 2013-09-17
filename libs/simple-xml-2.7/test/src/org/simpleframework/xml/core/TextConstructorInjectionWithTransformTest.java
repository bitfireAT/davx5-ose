package org.simpleframework.xml.core;

import java.io.StringWriter;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.transform.Matcher;
import org.simpleframework.xml.transform.Transform;

public class TextConstructorInjectionWithTransformTest extends TestCase {
   
   private static class DateTime {
      private final String time;
      public DateTime(){
         this.time = null;
      }
      public DateTime(String time) {
         this.time = time;
      }
      public String getTime() {
         return time;
      }
      public String toString() {
         return time;
      }
   }
   
   private static class DateTimeTransform implements Transform<DateTime> {
      public DateTime read(String text) throws Exception {
          return new DateTime(text);
      }
      public String write(DateTime value) throws Exception {
          return value.getTime();
      }
   }
   
   public class DateTimeMatcher implements Matcher {
      public Transform match(Class type) throws Exception {
         if(type == DateTime.class) {
            return new DateTimeTransform();
         }
         return null;
      }
   }
   
   @Root
   public static class Embargo {
      @Attribute
      private String name;
      @Text
      private DateTime embargo_datetime;
      public Embargo(@Text DateTime embargo_datetime, @Attribute(name="name") String name) {
          this.embargo_datetime = embargo_datetime;
          this.name = name;
      }
      public DateTime getEmbargo() {
          return embargo_datetime;
      }
      public void setEmbargo(DateTime embargo_datetime) {
          this.embargo_datetime = embargo_datetime;
      }
  }
    
   public void testConstructor() throws Exception {
      Embargo embargo = new Embargo(new DateTime("2010-06-02T12:00:12"), "cuba");
      Serializer serializer = new Persister(new DateTimeMatcher());      
      StringWriter writer = new StringWriter();
      
      serializer.write(embargo, writer);

      Embargo embargoout = serializer.read(Embargo.class, writer.toString());

      System.out.println(writer.toString());
   }

}
