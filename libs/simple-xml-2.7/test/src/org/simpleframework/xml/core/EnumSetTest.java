package org.simpleframework.xml.core;

import java.util.EnumSet;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class EnumSetTest extends ValidationTestCase {
   
   private static enum Qualification {
      BEGINNER,
      EXPERIENCED,
      EXPERT,
      GURU
   }
   
   @Root
   private static class EnumSetExample {
      
      @ElementList
      private EnumSet<Qualification> set = EnumSet.noneOf(Qualification.class);
      
      public EnumSetExample() {
         super();
      }
      
      public void add(Qualification qualification) {
         set.add(qualification);
      }
      
      public boolean contains(Qualification qualification) {
         return set.contains(qualification);
      }
   }
   
   public void testEnumSet() throws Exception {
      Persister persister = new Persister();
      EnumSetExample example = new EnumSetExample();
      
      example.add(Qualification.BEGINNER);
      example.add(Qualification.EXPERT);
      
      assertTrue(example.contains(Qualification.BEGINNER));
      assertTrue(example.contains(Qualification.EXPERT));
      assertFalse(example.contains(Qualification.GURU));

      persister.write(example, System.out);
      validate(persister, example);
   }

}
