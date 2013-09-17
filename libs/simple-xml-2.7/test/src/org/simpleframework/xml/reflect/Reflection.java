package org.simpleframework.xml.reflect;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class Reflection {


   public static String[] lookupParameterNames(AccessibleObject methodOrConstructor) {
      return lookupParameterNames(methodOrConstructor, true);
   }

   public static String[] lookupParameterNames(AccessibleObject methodOrCtor,
         boolean throwExceptionIfMissing) {

      Class<?>[] types = null;
      Class<?> declaringClass = null;
      String name = null;
      if (methodOrCtor instanceof Method) {
         Method method = (Method) methodOrCtor;
         types = method.getParameterTypes();
         name = method.getName();
         declaringClass = method.getDeclaringClass();
      } else {
         Constructor<?> constructor = (Constructor<?>) methodOrCtor;
         types = constructor.getParameterTypes();
         declaringClass = constructor.getDeclaringClass();
         name = "<init>";
      }

      if (types.length == 0) {
         return TypeCollector.EMPTY_NAMES;
      }
      InputStream content = getClassAsStream(declaringClass);
      if (content == null) {
         if (throwExceptionIfMissing) {
            throw new RuntimeException("Unable to get class bytes");
         } else {
            return TypeCollector.EMPTY_NAMES;
         }
      }
      try {
         ClassReader reader = new ClassReader(content);
         TypeCollector visitor = new TypeCollector(name, types,
               throwExceptionIfMissing);
         reader.accept(visitor);
         return visitor.getParameterNamesForMethod();
      } catch (IOException e) {
         if (throwExceptionIfMissing) {
            throw new RuntimeException(
                  "IoException while reading class bytes", e);
         } else {
            return TypeCollector.EMPTY_NAMES;
         }
      }
   }

   private static InputStream getClassAsStream(Class<?> clazz) {
      ClassLoader classLoader = clazz.getClassLoader();
      if (classLoader == null) {
         classLoader = ClassLoader.getSystemClassLoader();
      }
      return getClassAsStream(classLoader, clazz.getName());
   }

   private static InputStream getClassAsStream(ClassLoader classLoader,
         String className) {
      String name = className.replace('.', '/') + ".class";
      // better pre-cache all methods otherwise this content will be loaded
      // multiple times
      InputStream asStream = classLoader.getResourceAsStream(name);
      if (asStream == null) {
         asStream = Reflection.class.getResourceAsStream(name);
      }
      return asStream;
   }
}
