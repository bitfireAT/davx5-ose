package org.simpleframework.xml.reflect;

import java.lang.reflect.Method;

import junit.framework.TestCase;


public class ReflectionTest extends TestCase {

   static final String[] EMPTY_NAMES = new String[0];

   
   private static String someMethod(int anInt, String someString, Class thisIsAType) {
      return null;
   }

   public void testParameterNames() throws Exception {
      Method method = ReflectionTest.class.getDeclaredMethod("someMethod", new Class[]{int.class, String.class, Class.class});
      Reflection namer = new Reflection();
      String[] names = namer.lookupParameterNames(method, true);
      
      assertEquals("anInt", names[0]);
      assertEquals("someString", names[1]);
      assertEquals("thisIsAType", names[2]);
   }
}