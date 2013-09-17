package org.simpleframework.xml.core;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.DefaultType;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Transient;
import org.simpleframework.xml.ValidationTestCase;

public class DefaultWithParametersInGetterTest extends ValidationTestCase {

   @Root
   @Default(DefaultType.PROPERTY)
   static class DefaultTestClass {
      private boolean flag;
      private int foo;
      public int getFoo() {
         return foo;
      }
      public void setFoo(int foo) {
         this.foo = foo;
      }
      public String getWithParams(int foo) {
         return "foo";
      }
      public boolean isFlag(){
         return flag;
      }
      public void setFlag(boolean flag) {
         this.flag = flag;
      }
   }
   
   @Root
   @Default(DefaultType.PROPERTY)
   static class DefaultTestClassWithInvalidTransient {
      private int foo;
      public int getFoo() {
         return foo;
      }
      public void setFoo(int foo) {
         this.foo = foo;
      }
      @Transient
      public String getWithParams(int foo) {
         return "foo";
      }
   }
   
   @Root
   @Default(DefaultType.PROPERTY)
   static class DefaultTestClassWithInvalidElement {
      private String name;
      @Element
      public String getName(int foo) {
         return name;
      }
      @Element
      public void setName(String name) {
         this.name = name;
      }
   }
   
   public void testDefaultWithParameters() throws Exception{
      Persister persister = new Persister();
      DefaultTestClass type = new DefaultTestClass();
      type.foo = 100;
      persister.write(type, System.out);
      validate(type, persister);
   }
   
   public void testDefaultWithTransientErrors() throws Exception{
      Persister persister = new Persister();
      DefaultTestClassWithInvalidTransient type = new DefaultTestClassWithInvalidTransient();
      type.foo = 100;
      boolean failure = false;
      
      try {
         persister.write(type, System.out);
      }catch(Exception e) {
         e.printStackTrace();
         failure=true;
      }
      assertTrue("Annotation on a method which is not a property succeeded", failure);
   }
   
   public void testDefaultWithElementErrors() throws Exception{
      Persister persister = new Persister();
      DefaultTestClassWithInvalidElement type = new DefaultTestClassWithInvalidElement();
      type.name = "name";
      boolean failure = false;
      
      try {
         persister.write(type, System.out);
      }catch(Exception e) {
         e.printStackTrace();
         failure=true;
      }
      assertTrue("Annotation on a method which is not a property succeeded", failure);
   }
}
