package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.EnumMap;

import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class EnumMapTest extends ValidationTestCase {
   
   private static enum Number {
      ONE,
      TWO,
      THREE,
      FOUR
   }
   
   @Root
   private static class EnumMapExample {
      
      @ElementMap
      private EnumMap<Number, String> numbers = new EnumMap<Number, String>(Number.class);
      
      private EnumMapExample() {
         super();
      }
      
      public EnumMapExample(EnumMap<Number, String> numbers) {
         this.numbers = numbers;
      }
      
      public String get(Number number) {
         return numbers.get(number);
      }
   }
   
   public void testEnumMap() throws Exception {
      EnumMap<Number, String> numbers = new EnumMap<Number, String>(Number.class);
      
      numbers.put(Number.ONE, "1");
      numbers.put(Number.TWO, "2");
      numbers.put(Number.THREE, "3");
      
      EnumMapExample example = new EnumMapExample(numbers);
      Persister persister = new Persister();
      StringWriter out = new StringWriter();
      
      persister.write(example, System.out);
      persister.write(example, out);
      
      EnumMapExample other = persister.read(EnumMapExample.class, out.toString());
      
      assertEquals(other.get(Number.ONE), "1");
      assertEquals(other.get(Number.TWO), "2");
      assertEquals(other.get(Number.THREE), "3");
      assertEquals(other.get(Number.FOUR), null);
      
      persister.write(example, System.out);
      
      validate(persister, example);
   }
}
