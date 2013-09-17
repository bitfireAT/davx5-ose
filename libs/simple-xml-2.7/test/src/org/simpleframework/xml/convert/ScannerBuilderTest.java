package org.simpleframework.xml.convert;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import junit.framework.TestCase;

public class ScannerBuilderTest extends TestCase {
   
   @Retention(RetentionPolicy.RUNTIME)
   public static @interface One{}
   @Retention(RetentionPolicy.RUNTIME)
   public static @interface Two{}
   @Retention(RetentionPolicy.RUNTIME)
   public static @interface Three{}
   @Retention(RetentionPolicy.RUNTIME)
   public static @interface Four{}
   
   @One
   @Two
   public class Base {}
   
   @Three
   @Four
   public class Extended extends Base{}
   
   public void testScannerBuilder() throws Exception {
      ScannerBuilder builder = new ScannerBuilder();
      Scanner scanner = builder.build(Extended.class);
      
      assertNull(scanner.scan(Convert.class));
      assertNotNull(scanner.scan(One.class));
      assertNotNull(scanner.scan(Two.class));
      assertNotNull(scanner.scan(Three.class));
      assertNotNull(scanner.scan(Four.class));
      assertEquals(scanner.scan(Convert.class), null);
      assertTrue(One.class.isAssignableFrom(scanner.scan(One.class).getClass()));
      assertTrue(Two.class.isAssignableFrom(scanner.scan(Two.class).getClass()));
      assertTrue(Three.class.isAssignableFrom(scanner.scan(Three.class).getClass()));
      assertTrue(Four.class.isAssignableFrom(scanner.scan(Four.class).getClass()));
   }

}
