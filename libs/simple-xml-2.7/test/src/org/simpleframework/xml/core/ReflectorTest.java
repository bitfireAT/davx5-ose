package org.simpleframework.xml.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;

import org.simpleframework.xml.core.Reflector;

import junit.framework.TestCase;

public class ReflectorTest extends TestCase {
   
   public Collection<String> genericList;
   public Collection normalList;
   
   public Map<Float, Double> genericMap;
   public Map normalMap;
   
   public void genericMethodMapParameter(Map<String, Integer> map) {}
   public void normalMethodMapParameter(Map map) {}  
   public void genericMethodCollectionParameter(Collection<String> list) {}
   public void normalMethodCollectionParameter(Collection list){}
   
   public Map<String, Boolean> genericMethodMapReturn() {return null;}
   public Map normalMethodMapReturn() {return null;}
   public Collection<Float> genericMethodCollectionReturn() {return null;}
   public Collection normalMethodCollectionReturn() {return null;}
   
   
   public void testFieldReflector() throws Exception {
      Field field = getField(ReflectorTest.class, "genericMap");
      Class[] types = Reflector.getDependents(field);
      
      assertEquals(types.length, 2);
      assertEquals(types[0], Float.class);
      assertEquals(types[1], Double.class);
      
      field = getField(ReflectorTest.class, "normalMap");
      types = Reflector.getDependents(field);
      
      assertEquals(types.length, 0);
      
      field = getField(ReflectorTest.class, "genericList");
      types = Reflector.getDependents(field);
      
      assertEquals(types.length, 1);
      assertEquals(types[0], String.class);
      
      field = getField(ReflectorTest.class, "normalList");
      types = Reflector.getDependents(field);
      
      assertEquals(types.length, 0);
      
   }
   
   public void testCollectionReflector() throws Exception {
      Method method = getMethod(ReflectorTest.class, "genericMethodCollectionParameter", Collection.class);
      Class[] types = Reflector.getParameterDependents(method, 0);
      
      assertEquals(types.length, 1);
      assertEquals(types[0], String.class); 
      
      method = getMethod(ReflectorTest.class, "normalMethodCollectionParameter", Collection.class);
      types = Reflector.getParameterDependents(method, 0);     
      
      assertEquals(types.length, 0);
      
      method = getMethod(ReflectorTest.class, "genericMethodCollectionReturn");
      types = Reflector.getReturnDependents(method);
      
      assertEquals(types.length, 1);
      assertEquals(types[0], Float.class);
      
      method = getMethod(ReflectorTest.class, "normalMethodCollectionReturn");
      types = Reflector.getReturnDependents(method);
      
      assertEquals(types.length, 0);    
   }
      
   
   public void testMapReflector() throws Exception {
      Method method = getMethod(ReflectorTest.class, "genericMethodMapParameter", Map.class);
      Class[] types = Reflector.getParameterDependents(method, 0);
      
      assertEquals(types.length, 2);
      assertEquals(types[0], String.class);
      assertEquals(types[1], Integer.class); 
      
      method = getMethod(ReflectorTest.class, "normalMethodMapParameter", Map.class);
      types = Reflector.getParameterDependents(method, 0);     
      
      assertEquals(types.length, 0);
      
      method = getMethod(ReflectorTest.class, "genericMethodMapReturn");
      types = Reflector.getReturnDependents(method);
      
      assertEquals(types.length, 2);
      assertEquals(types[0], String.class);
      assertEquals(types[1], Boolean.class);
      
      method = getMethod(ReflectorTest.class, "normalMethodMapReturn");
      types = Reflector.getReturnDependents(method);
      
      assertEquals(types.length, 0);      
   }
   
   public Method getMethod(Class type, String name, Class... types) throws Exception {
      return type.getDeclaredMethod(name, types);
   }
   
   public Field getField(Class type, String name) throws Exception {
      return type.getDeclaredField(name);
   }
   
   public void testCase() throws Exception {
      assertEquals("URL", Reflector.getName("URL"));
      assertEquals("getEntry", Reflector.getName("getEntry"));
      assertEquals("iF", Reflector.getName("iF"));
      assertEquals("if", Reflector.getName("if"));
      assertEquals("URLConnection", Reflector.getName("URLConnection"));
      assertEquals("type", Reflector.getName("Type"));
   }
}
