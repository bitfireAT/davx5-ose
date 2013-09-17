package org.simpleframework.xml.core;

import java.io.Serializable;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.Verbosity;

public class NoAnnotationsRequiredTest extends ValidationTestCase {
   
   public static class Thing {
      public String thing = "thing";
   }
   
   public static class Person extends Thing {
      private String name = "Jim";
      private String surname = "Beam";
      private int age = 90;
      private Address address = new Address();
      private List<String> hobbies = new ArrayList<String>();
      public Person() {
         hobbies.add("Soccer");
         hobbies.add("Golf");
         hobbies.add("Tennis");
      }
   }
   
   public static class Address {
      private String street = "10 Some Street";
      private String city = "New York";
      private String country = "US";
   }
   
   private static class PrimitiveEntry implements Serializable {// Verbosity {HIGH,LOW}
      private transient String transientText = "THIS TEXT IS NOT SERIALZED";
      private boolean booleanValue = false; 
      private byte byteValue = 6;
      private short shortValue = 78;
      private int intValue = 102353;   
      private float floatValue = 93.342f;
      private long longValue = 102L; 
      private double doubleValue = 34.2;
      private String stringValue = "text";
      private Date dateValue = new Date();
      private byte[] byteArray = new byte[]{1,2,3,4,5,6,7,8,9};
      private byte[][] byteArrayArray = new byte[][]{{1,2,3,4,5,6,7,8,9},{1,2,3,4,5,6,7,8,9}};
      private String[] stringArray = new String[] {"A", "B", "C"};
      private Map<Double, String> doubleToString = new HashMap<Double, String>();
      public PrimitiveEntry() {
         doubleToString.put(1.0, "ONE");
         doubleToString.put(12.0, "TWELVE");
      }
   }
   
   public void testPerformance() throws Exception {
      Format format = new Format(0, Verbosity.LOW);
      Persister persister = new Persister(format);
      PrimitiveEntry entry = new PrimitiveEntry();
      StringWriter writer = new StringWriter();
      Marshaller marshaller = new CompressionMarshaller(Compression.NONE);
      int iterations = 1000;
      String xmlText = null;
      String binaryText = null;
         
      for(int i = 0; i < iterations; i++) {
         persister.write(entry, writer);
         xmlText = writer.getBuffer().toString();
         if(i == 0) {
            System.err.println(xmlText);
         }
         writer.getBuffer().setLength(0);

      }
      for(int i = 0; i < iterations; i++) {
         binaryText = marshaller.marshallText(entry, PrimitiveEntry.class);
         if(i == 0) {
            System.err.println(binaryText);
         }
      }
      long startTime = System.currentTimeMillis();
      for(int i = 0; i < iterations; i++) {
         persister.write(entry, writer);
         writer.getBuffer().toString();
         writer.getBuffer().setLength(0);
      }
      long duration = System.currentTimeMillis() - startTime;
      
      System.err.println("Time for writing XML: " + duration + " 1 per " + ((double)duration / (double)iterations));
      
      startTime = System.currentTimeMillis();
      for(int i = 0; i < iterations; i++) {
         marshaller.marshallText(entry, PrimitiveEntry.class);
      }
      duration = System.currentTimeMillis() - startTime;
      
      System.err.println("Time for writing BINARY: " + duration + " 1 per " + ((double)duration / (double)iterations));
      
      startTime = System.currentTimeMillis();
      for(int i = 0; i < iterations; i++) {
         persister.write(entry, writer);
         writer.getBuffer().toString();
         writer.getBuffer().setLength(0);
      }
      duration = System.currentTimeMillis() - startTime;
      
      System.err.println("Time for writing XML: " + duration + " 1 per " + ((double)duration / (double)iterations));
      
      startTime = System.currentTimeMillis();
      for(int i = 0; i < iterations; i++) {
         marshaller.marshallText(entry, PrimitiveEntry.class);
      }
      duration = System.currentTimeMillis() - startTime;
      
      System.err.println("Time for writing BINARY: " + duration + " 1 per " + ((double)duration / (double)iterations));
      
      // Test the writing...;
      
      startTime = System.currentTimeMillis();
      for(int i = 0; i < iterations; i++) {
         persister.read(PrimitiveEntry.class, xmlText);
      }
      duration = System.currentTimeMillis() - startTime;
      
      System.err.println("Time for reading XML: " + duration + " 1 per " + ((double)duration / (double)iterations));
      
      startTime = System.currentTimeMillis();
      for(int i = 0; i < iterations; i++) {
         marshaller.unmarshallText(binaryText, PrimitiveEntry.class);
      }
      duration = System.currentTimeMillis() - startTime;
      
      System.err.println("Time for reading BINARY: " + duration + " 1 per " + ((double)duration / (double)iterations));
      
      startTime = System.currentTimeMillis();
      for(int i = 0; i < iterations; i++) {
         persister.read(PrimitiveEntry.class, xmlText);
      }
      duration = System.currentTimeMillis() - startTime;
      
      System.err.println("Time for reading XML: " + duration + " 1 per " + ((double)duration / (double)iterations));
      
      startTime = System.currentTimeMillis();
      for(int i = 0; i < iterations; i++) {
         marshaller.unmarshallText(binaryText, PrimitiveEntry.class);
      }
      duration = System.currentTimeMillis() - startTime;
      
      System.err.println("Time for reading BINARY: " + duration + " 1 per " + ((double)duration / (double)iterations));
      
      startTime = System.currentTimeMillis();
      for(int i = 0; i < iterations; i++) {
         persister.read(PrimitiveEntry.class, xmlText);
      }
      duration = System.currentTimeMillis() - startTime;
      
      System.err.println("Time for reading XML: " + duration + " 1 per " + ((double)duration / (double)iterations));
      
      startTime = System.currentTimeMillis();
      for(int i = 0; i < iterations; i++) {
         marshaller.unmarshallText(binaryText, PrimitiveEntry.class);
      }
      duration = System.currentTimeMillis() - startTime;
      
      System.err.println("Time for reading BINARY: " + duration + " 1 per " + ((double)duration / (double)iterations));
      
      validate(persister, entry);
   }
   
   public void testType() throws Exception {
      Persister persister = new Persister();
      PrimitiveEntry entry = new PrimitiveEntry();
      StringWriter writer = new StringWriter();
      
      persister.write(entry, writer);
      validate(persister, entry);
   }
   
   public void testTypeVerbose() throws Exception {
      Format format = new Format(Verbosity.LOW);
      Persister persister = new Persister(format);
      PrimitiveEntry entry = new PrimitiveEntry();
      StringWriter writer = new StringWriter();
      
      persister.write(entry, writer);
      validate(persister, entry);
   }

   public void testSerialization() throws Exception {
      Persister persister = new Persister();
      Person person = new Person();
      StringWriter writer = new StringWriter();
      
      persister.write(person, writer);
      validate(persister, person);
      
      String text = writer.toString();
      
      assertElementExists(text, "/person");
      assertElementHasValue(text, "/person/thing", "thing");
      assertElementHasValue(text, "/person/name", "Jim");
      assertElementHasValue(text, "/person/surname", "Beam");
      assertElementHasValue(text, "/person/age", "90");
      assertElementExists(text, "/person/address");
      assertElementHasValue(text, "/person/address/street", "10 Some Street");
      assertElementHasValue(text, "/person/address/city", "New York");
      assertElementHasValue(text, "/person/address/country", "US");
      assertElementExists(text, "/person/hobbies");
      assertElementHasValue(text, "/person/hobbies/string[1]", "Soccer");
      assertElementHasValue(text, "/person/hobbies/string[2]", "Golf");
      assertElementHasValue(text, "/person/hobbies/string[3]", "Tennis");
   }

}
