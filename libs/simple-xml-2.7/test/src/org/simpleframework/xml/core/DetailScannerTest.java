package org.simpleframework.xml.core;

import java.util.Arrays;

import junit.framework.TestCase;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.DefaultType;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Root;

public class DetailScannerTest extends TestCase {

   @Root(name="base")
   @Namespace(reference="http://www/")
   public static class BaseExample {
     private String x;
     public String getX(){
        return x;
     }
     public void setX(String x){
        this.x = x;
     }
   }
   
   @Root(name="detail")
   @Default
   @NamespaceList({
      @Namespace(reference="http://x.com/", prefix="x"),
      @Namespace(reference="http://y.com/", prefix="y")
   })
   private static class DetailExample extends BaseExample{
      private String value;
      @Validate
      public void validate() {}
      public String getValue() {
         return value;
      }
      public void setValue(String value) {
         this.value = value;
      }
   }
   
   public void testScanner() throws Exception {
      DetailScanner scanner = new DetailScanner(DetailExample.class);
      
      assertEquals(scanner.getNamespace(), null);
      assertEquals(scanner.getNamespaceList().value().length, 2);
      assertEquals(scanner.getNamespaceList().value()[0].reference(), "http://x.com/");
      assertEquals(scanner.getNamespaceList().value()[0].prefix(), "x");
      assertEquals(scanner.getNamespaceList().value()[1].reference(), "http://y.com/");
      assertEquals(scanner.getNamespaceList().value()[1].prefix(), "y");
      assertEquals(scanner.getNamespaceList().value().length, 2);
      assertEquals(scanner.getRoot().name(), "detail");
      assertEquals(scanner.getAccess(), DefaultType.FIELD);
      assertEquals(scanner.getMethods().size(), 3);
      assertEquals(scanner.getFields().size(), 1);
      assertEquals(scanner.getFields().get(0).getName(), "value");
      assertTrue(Arrays.asList(scanner.getMethods().get(0).getName(), 
                               scanner.getMethods().get(1).getName(),
                                scanner.getMethods().get(2).getName()).containsAll(Arrays.asList("validate", "getValue", "setValue")));
      
      for(MethodDetail detail : scanner.getMethods()) {
         if(detail.getName().equals("validate")) {
            assertEquals(detail.getAnnotations().length, 1);
            assertEquals(detail.getAnnotations()[0].annotationType(), Validate.class);
         }
      }
      assertTrue(scanner.getMethods() == scanner.getMethods());
      assertTrue(scanner.getNamespaceList() == scanner.getNamespaceList());
      assertTrue(scanner.getRoot() == scanner.getRoot());
      assertTrue(scanner.getAccess() == scanner.getAccess());
   }
}
