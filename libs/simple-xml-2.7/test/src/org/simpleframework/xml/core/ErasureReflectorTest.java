package org.simpleframework.xml.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import org.simpleframework.xml.ValidationTestCase;

public class ErasureReflectorTest extends ValidationTestCase {

   private static class MapErasure<T> {
      public static Field getField(String name) throws Exception {
         return MapErasure.class.getField(name);
      }
      public static Method getMethod(String name) throws Exception {
         if(name.startsWith("set")) {
            return MapErasure.class.getMethod(name, Map.class);
         }
         return MapErasure.class.getMethod(name);
      }
      public static Constructor getConstructor() throws Exception {
         return MapErasure.class.getDeclaredConstructors()[0];
      }
      private Map<T, T> erasedToErased;
      private Map<T, String> erasedToString;
      private Map<String, T> stringToErased;
      private Map<String, String> stringToString;
      public MapErasure(
            Map<T, T> erasedToErased,
            Map<T, String> erasedToString,
            Map<String, T> stringToErased,
            Map<String, String> stringToString) {
      }
      public Map<T, T> getErasedToErased() {
         return erasedToErased;
      }
      public Map<T, String> getErasedToString() {
         return erasedToString;
      }
      public Map<String, T> getStringToErased() {
         return stringToErased;
      }
      public Map<String, String> getStringToString() {
         return stringToString;
      }
      public void setErasedToErased(Map<T, T> erasedToErased) {
         this.erasedToErased = erasedToErased;
      }
      public void setErasedToString(Map<T, String> erasedToString) {
         this.erasedToString = erasedToString;
      }
      public void setStringToErased(Map<String, T> stringToErased) {
         this.stringToErased = stringToErased;
      }
      public void setStringToString(Map<String, String> stringToString) {
         this.stringToString = stringToString;
      }
   }
   
   private static class CollectionErasure<T> {
      public static Field getField(String name) throws Exception {
         return CollectionErasure.class.getField(name);
      }
      public static Method getMethod(String name) throws Exception {
         if(name.startsWith("set")) {
            return CollectionErasure.class.getMethod(name, Collection.class);
         }
         return CollectionErasure.class.getMethod(name);
      }
      public static Constructor getConstructor() throws Exception {
         return CollectionErasure.class.getDeclaredConstructors()[0];
      }
      private Collection<T> erased;
      private Collection<String> string;
      public CollectionErasure(
            Collection<T> erased,
            Collection<String> string) {
      }
      public Collection<T> getErased() {
         return erased;
      }
      public Collection<String> getString() {
         return string;
      } 
      public void setErased(Collection<T> erased) {
         this.erased = erased;
      }
      public void setString(Collection<String> string) {
         this.string = string;
      }
   }
   
   public void tesFieldReflection() throws Exception {
      assertEquals(Object.class, Reflector.getDependent(CollectionErasure.getField("erased")));
      assertEquals(String.class, Reflector.getDependent(CollectionErasure.getField("string")));
      
      assertEquals(Object.class, Reflector.getDependent(MapErasure.getField("erasedToErased")));
      assertEquals(Object.class, Reflector.getDependent(MapErasure.getField("erasedToString")));
      assertEquals(String.class, Reflector.getDependent(MapErasure.getField("stringToErased")));
      assertEquals(String.class, Reflector.getDependent(MapErasure.getField("stringToString")));  
      
      assertEquals(Object.class, Reflector.getDependents(MapErasure.getField("erasedToErased"))[0]);
      assertEquals(Object.class, Reflector.getDependents(MapErasure.getField("erasedToString"))[0]);
      assertEquals(String.class, Reflector.getDependents(MapErasure.getField("stringToErased"))[0]);
      assertEquals(String.class, Reflector.getDependents(MapErasure.getField("stringToString"))[0]);
      assertEquals(Object.class, Reflector.getDependents(MapErasure.getField("erasedToErased"))[1]);
      assertEquals(String.class, Reflector.getDependents(MapErasure.getField("erasedToString"))[1]);
      assertEquals(Object.class, Reflector.getDependents(MapErasure.getField("stringToErased"))[1]);
      assertEquals(String.class, Reflector.getDependents(MapErasure.getField("stringToString"))[1]);
   }
   
   public void testMethodReflection() throws Exception {
      assertEquals(Object.class, Reflector.getReturnDependent(CollectionErasure.getMethod("getErased")));
      assertEquals(String.class, Reflector.getReturnDependent(CollectionErasure.getMethod("getString")));
      assertEquals(null, Reflector.getReturnDependent(CollectionErasure.getMethod("setErased")));
      assertEquals(null, Reflector.getReturnDependent(CollectionErasure.getMethod("setString")));
      
      assertEquals(Object.class, Reflector.getParameterDependent(CollectionErasure.getConstructor(), 0)); // Collection<T>
      assertEquals(String.class, Reflector.getParameterDependent(CollectionErasure.getConstructor(), 1)); // Collection<String>
      assertEquals(Object.class, Reflector.getParameterDependents(CollectionErasure.getConstructor(), 0)[0]); // Collection<T>
      assertEquals(String.class, Reflector.getParameterDependents(CollectionErasure.getConstructor(), 1)[0]); // Collection<String>
      
      assertEquals(Object.class, Reflector.getReturnDependent(MapErasure.getMethod("getErasedToErased")));
      assertEquals(Object.class, Reflector.getReturnDependent(MapErasure.getMethod("getErasedToString")));
      assertEquals(String.class, Reflector.getReturnDependent(MapErasure.getMethod("getStringToErased")));
      assertEquals(String.class, Reflector.getReturnDependent(MapErasure.getMethod("getStringToString")));      
      assertEquals(null, Reflector.getReturnDependent(MapErasure.getMethod("setErasedToErased")));
      assertEquals(null, Reflector.getReturnDependent(MapErasure.getMethod("setErasedToString")));
      assertEquals(null, Reflector.getReturnDependent(MapErasure.getMethod("setStringToErased")));
      assertEquals(null, Reflector.getReturnDependent(MapErasure.getMethod("setStringToString")));
      
      assertEquals(Object.class, Reflector.getReturnDependents(MapErasure.getMethod("getErasedToErased"))[0]);
      assertEquals(Object.class, Reflector.getReturnDependents(MapErasure.getMethod("getErasedToString"))[0]);
      assertEquals(String.class, Reflector.getReturnDependents(MapErasure.getMethod("getStringToErased"))[0]);
      assertEquals(String.class, Reflector.getReturnDependents(MapErasure.getMethod("getStringToString"))[0]);
      assertEquals(Object.class, Reflector.getReturnDependents(MapErasure.getMethod("getErasedToErased"))[1]);
      assertEquals(String.class, Reflector.getReturnDependents(MapErasure.getMethod("getErasedToString"))[1]);
      assertEquals(Object.class, Reflector.getReturnDependents(MapErasure.getMethod("getStringToErased"))[1]);
      assertEquals(String.class, Reflector.getReturnDependents(MapErasure.getMethod("getStringToString"))[1]);
      
      assertEquals(Object.class, Reflector.getParameterDependent(MapErasure.getConstructor(), 0)); // Map<T, T> 
      assertEquals(Object.class, Reflector.getParameterDependent(MapErasure.getConstructor(), 1)); // Map<T, String>
      assertEquals(String.class, Reflector.getParameterDependent(MapErasure.getConstructor(), 2)); // Map<String, T>
      assertEquals(String.class, Reflector.getParameterDependent(MapErasure.getConstructor(), 3)); // Map<String, String>
      
      assertEquals(Object.class, Reflector.getParameterDependents(MapErasure.getConstructor(), 0)[0]); // Map<T, T> 
      assertEquals(Object.class, Reflector.getParameterDependents(MapErasure.getConstructor(), 1)[0]); // Map<T, String>
      assertEquals(String.class, Reflector.getParameterDependents(MapErasure.getConstructor(), 2)[0]); // Map<String, T>
      assertEquals(String.class, Reflector.getParameterDependents(MapErasure.getConstructor(), 3)[0]); // Map<String, String>
      assertEquals(Object.class, Reflector.getParameterDependents(MapErasure.getConstructor(), 0)[1]); // Map<T, T> 
      assertEquals(String.class, Reflector.getParameterDependents(MapErasure.getConstructor(), 1)[1]); // Map<T, String>
      assertEquals(Object.class, Reflector.getParameterDependents(MapErasure.getConstructor(), 2)[1]); // Map<String, T>
      assertEquals(String.class, Reflector.getParameterDependents(MapErasure.getConstructor(), 3)[1]); // Map<String, String>   
   }
   
}
