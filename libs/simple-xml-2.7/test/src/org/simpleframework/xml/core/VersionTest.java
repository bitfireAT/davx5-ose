package org.simpleframework.xml.core;

import java.lang.reflect.Constructor;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.Version;
import org.simpleframework.xml.strategy.Value;

public class VersionTest extends TestCase {      

   private static final String VERSION_1 =
   "<?xml version=\"1.0\"?>\n"+
   "<Example version='1.0'>\n"+
   "   <text>text value</text>  \n\r"+
   "</Example>";

   private static final String VERSION_2 =
   "<?xml version=\"1.0\"?>\n"+
   "<Example version='2.0'>\n"+
   "   <name>example name</name>  \n\r"+
   "   <value>text value</value> \n"+
   "   <entry name='example'>\n"+   
   "      <value>text value</value> \n"+   
   "   </entry>\n"+
   "   <ignore>ignore this element</ignore>\n"+
   "</Example>";
   
   
   public interface Versionable {
      
      public double getVersion();
   }

   @Root(name="Example")
   private static abstract class Example implements Versionable {

      @Version
      @Namespace(prefix="prefix", reference="http://www.domain.com/reference")
      private double version;
      
      public double getVersion() {
         return version;
      }
      
      public abstract String getValue();   
   }
   
   private static class Example1 extends Example {

      @Element(name="text")
      private String text;           

      public String getValue() {
         return text;              
      }
   }

   private static class Example2 extends Example {          

      @Element(name="name")
      private String name;

      @Element(name="value")
      private String value;

      @Element(name="entry")
      private Entry entry;

      public String getValue() {
         return value;              
      }
   }

   private static class Entry {

      @Attribute(name="name")
      private String name;           

      @Element(name="value")
      private String value;              
   }
   
   public static class SimpleType implements Value{
      
      private Class type;
      
      public SimpleType(Class type) {
         this.type = type;
      }
      
      public int getLength() {
         return 0;
      }
      
      public Object getValue()  {
         try {
            Constructor method = type.getDeclaredConstructor();
   
            if(!method.isAccessible()) {
               method.setAccessible(true);              
            }
            return method.newInstance();   
         }catch(Exception e) {
            throw new RuntimeException(e);
         }
      }   
       
       public void setValue(Object value) {
       }
       
       public boolean isReference() {
          return false;
       }
       
      public Class getType() {
        return type;
      } 
   }
   
   public void testVersion1() throws Exception {           
      Serializer persister = new Persister();
      Example example = persister.read(Example1.class, VERSION_1);
      
      assertTrue(example instanceof Example1);
      assertEquals(example.getValue(), "text value");
      assertEquals(example.version, 1.0);
      
      persister.write(example, System.out);
      
      assertEquals(example.getValue(), "text value");
      assertEquals(example.version, 1.0);
   }

   public void testVersion2() throws Exception {           
      Serializer persister = new Persister();
      Example example = persister.read(Example2.class, VERSION_2);      
      
      assertTrue(example instanceof Example2);
      assertEquals(example.getValue(), "text value");
      assertEquals(example.version, 2.0);
      
      persister.write(example, System.out);
      
      assertEquals(example.getValue(), "text value");
      assertEquals(example.version, 2.0);
   }
}
