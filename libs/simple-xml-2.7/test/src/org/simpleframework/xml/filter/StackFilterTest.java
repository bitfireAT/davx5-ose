package org.simpleframework.xml.filter;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

public class StackFilterTest extends TestCase {
   
   public static class ExampleFilter implements Filter {
      
      private List<String> list;
      private String name;
      
      public ExampleFilter(List<String> list, String name) {
         this.list = list;
         this.name = name;
      }
      
      public String replace(String token) { 
         if(token == name) {
            list.add(name);
            return name;
         }
         return null;
      }
   }
  
   public void testFilter() {
      List<String> list = new ArrayList<String>();
      StackFilter filter = new StackFilter();
      
      filter.push(new ExampleFilter(list, "one"));
      filter.push(new ExampleFilter(list, "two"));
      filter.push(new ExampleFilter(list, "three"));
      
      String one = filter.replace("one");
      String two = filter.replace("two");
      String three = filter.replace("three");
      
      assertEquals(one, "one");
      assertEquals(two, "two");
      assertEquals(three, "three");
      assertEquals(list.size(), 3);
      assertEquals(list.get(0), "one");
      assertEquals(list.get(1), "two");
      assertEquals(list.get(2), "three");  
   }
}
