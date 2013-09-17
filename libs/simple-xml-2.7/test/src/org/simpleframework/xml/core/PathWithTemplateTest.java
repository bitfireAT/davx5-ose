package org.simpleframework.xml.core;

import java.util.HashMap;
import java.util.Map;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.filter.Filter;
import org.simpleframework.xml.filter.MapFilter;

public class PathWithTemplateTest extends ValidationTestCase {
   
   private static final String SOURCE =
   "<pathWithTemplateExample>\n" +
   "   <details>\n" +
   "      <name>${name}</name>\n" +
   "      <age>${age}</age>\n" +
   "      <sex>${sex}</sex>\n" +
   "      <address>\n" +
   "         <street>${street}</street>\n" +
   "         <city>${city}</city>\n" +
   "      </address>\n" +
   "      <phone area='${area}'>\n" +
   "         <mobile>${mobile}</mobile>\n" +
   "         <home>${home}</home>\n" +
   "      </phone>\n" +
   "   </details>\n" +
   "</pathWithTemplateExample>\n";

   @Default
   private static class PathWithTemplateExample {
      @Path("details")
      private String name;
      @Path("details")
      private int age;
      @Path("details")
      private Sex sex;
      @Path("details/address")
      private String street;
      @Path("details/address")
      private String city;
      @Attribute
      @Path("details/phone")
      private String area;
      @Path("details/phone")
      private String mobile;
      @Path("details/phone")
      private String home;
      public void setName(String name) {
         this.name = name;
      }
      public void setAge(int age) {
         this.age = age;
      }
      public void setSex(Sex sex) {
         this.sex = sex;
      }
      public void setStreet(String street) {
         this.street = street;
      }
      public void setCity(String city) {
         this.city = city;
      }
      public void setArea(String area) {
         this.area = area;
      }
      public void setMobile(String mobile) {
         this.mobile = mobile;
      }
      public void setHome(String home) {
         this.home = home;
      }  
   }
   
   private static enum Sex{
      MALE,
      FEMALE
   }
   
   public void testSerialization() throws Exception {
      Persister persister = new Persister();
      PathWithTemplateExample example = new PathWithTemplateExample();
      example.setArea("+61");
      example.setName("John Doe");
      example.setSex(Sex.MALE);
      example.setAge(21);
      example.setCity("Sydney");
      example.setMobile("12345");
      example.setHome("67890");
      example.setStreet("George Street");
      persister.write(example, System.err);
      validate(example, persister);
   }
   
   public void testPathWithTemplates() throws Exception {
      Map<String, String> map = new HashMap<String, String>();
      map.put("name", "Jack Daniels");
      map.put("age", "22");
      map.put("sex", "MALE");
      map.put("city", "London");
      map.put("street", "Old Street");
      map.put("mobile", "12345");
      map.put("home", "67890");
      map.put("area", "+44");
      Filter filter = new MapFilter(map);
      Persister persister = new Persister(filter);
      PathWithTemplateExample example = persister.read(PathWithTemplateExample.class, SOURCE);
      assertEquals(example.area, "+44");
      assertEquals(example.name, "Jack Daniels");
      assertEquals(example.sex, Sex.MALE);
      assertEquals(example.age, 22);
      assertEquals(example.city, "London");
      assertEquals(example.mobile, "12345");
      assertEquals(example.home, "67890");
      assertEquals(example.street, "Old Street");
      persister.write(example, System.err);
      validate(example, persister);
   }
}
