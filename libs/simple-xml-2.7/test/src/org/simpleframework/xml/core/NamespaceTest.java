package org.simpleframework.xml.core;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;

public class NamespaceTest extends ValidationTestCase {
   
   @Root
   @NamespaceList({
      @Namespace(prefix="tax", reference="http://www.domain.com/tax"),
      @Namespace(reference="http://www.domain.com/default")
   })
   @Namespace(prefix="per", reference="http://www.domain.com/person")
   private static class Person {
      
      @Element
      private Profession job;
      
      @Element
      private String name;
      
      @Element
      private String value;
      
      @Attribute
      private int age;
      
      private Person() {
         super();
      }
      
      public Person(String name, String value, int age, Profession job) {
         this.name = name;
         this.value = value;
         this.age  = age;
         this.job = job;
      }
      
      public Profession getJob() {
         return job;
      }
      
      public String getName() {
         return name;
      }
   }
   
   @Root
   @Namespace(prefix="jb", reference="http://www.domain.com/job")
   private static class Profession {
      
      @Element
      private String title;
      
      @Attribute
      @Namespace(reference="http://www.domain.com/tax")
      private int salary;
      
      @Attribute
      private int experience;
      
      @Namespace
      @Element
      private Employer employer;
      
      private Profession() {
         super();
      }
      
      public Profession(String title, int salary, int experience, Employer employer) {
         this.title = title;
         this.salary = salary;
         this.experience = experience;
         this.employer = employer;
      }
      
      public Employer getEmployer() {
         return employer;
      }
      
      public String getTitle() {
         return title;
      }
   }
   
   @Root
   private static class Employer {
      
      @Element
      @Namespace(reference="http://www.domain.com/employer")
      private String name;
      
      @Element
      @Namespace(prefix="count", reference="http://www.domain.com/count")
      private int employees;
      
      @Element
      private String address;
      
      @Attribute
      private boolean international;
      
      private Employer() {
         super();
      }
      
      public Employer(String name, String address, boolean international, int employees) {
         this.name = name;
         this.employees = employees;
         this.address = address;
         this.international = international;
      }
      
      public String getAddress() {
         return address;
      }
      
      public String getName() {
         return name;
      }
   }
   
   public void testNamespace() throws Exception {
      Persister persister = new Persister();
      Employer employer = new Employer("Spam Soft", "Sesame Street", true, 1000);
      Profession job = new Profession("Software Engineer", 10, 12, employer);
      Person person = new Person("John Doe", "Person", 30, job);
      
      persister.write(person, System.out);
      
      validate(persister, person);
   }

}
