package org.simpleframework.xml.core;

import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;

public class FloatingTextTest extends ValidationTestCase {
   
   private static final String SOURCE =
   "<test>An int follows <int>12</int> A double follows <double>33.3</double></test>\n";
   
   private static final String SOURCE_WITH_ATTRIBUTES =
   "<test a='A' b='b'>An int follows <int>12</int> A double follows <double>33.3</double></test>\n";
   
   private static final String SOURCE_WITH_CHILD =
   "<test><child>An int follows <int>12</int> A double follows <double>33.3</double></child></test>\n";

   @Root
   private static class Example {
      
      @Text
      @ElementListUnion({
         @ElementList(entry="int", type=Integer.class, required=false, inline=true),
         @ElementList(entry="double", type=Double.class, required=false, inline=true)
      })
      private List<Object> list;
   }
   
   @Root
   private static class ExampleWithElementsIsAnError {
      
      @Element(required=false)
      private String value;
      
      @Text
      @ElementListUnion({
         @ElementList(entry="int", type=Integer.class, required=false, inline=true),
         @ElementList(entry="double", type=Double.class, required=false, inline=true)
      })
      private List<Object> list;
   }
   
   @Root
   private static class ExampleWithTextIsAnError {
      
      @Text(required=false)
      private String value;
      
      @Text
      @ElementListUnion({
         @ElementList(entry="int", type=Integer.class, required=false, inline=true),
         @ElementList(entry="double", type=Double.class, required=false, inline=true)
      })
      private List<Object> list;
   }
   
   @Root
   private static class ExampleWithAttributeNotAnError {
      
      @Attribute
      private String a;
      
      @Attribute
      private String b;
      
      @Text
      @ElementListUnion({
         @ElementList(entry="int", type=Integer.class, required=false, inline=true),
         @ElementList(entry="double", type=Double.class, required=false, inline=true)
      })
      private List<Object> list;
   }
   
   @Root
   private static class ExampleWithPathAnError {
      
      @Path("p")
      @Attribute
      private String a;
      
      @Path("p")
      @Attribute
      private String b;
      
      @Text
      @ElementListUnion({
         @ElementList(entry="int", type=Integer.class, required=false, inline=true),
         @ElementList(entry="double", type=Double.class, required=false, inline=true)
      })
      private List<Object> list;
   }
   
   @Root
   private static class ExampleWithStringElementInAnError {
      
      @Attribute
      private String a;
      
      @Attribute
      private String b;
      
      @Text
      @ElementListUnion({
         @ElementList(entry="int", type=Integer.class, required=false, inline=true),
         @ElementList(entry="double", type=Double.class, required=false, inline=true),
         @ElementList(entry="string", type=String.class, required=false, inline=true)
      })
      private List<Object> list;
   }
   
   @Root
   private static class ExampleWithPath {
      
      @Path("child")
      @Text
      @ElementListUnion({
         @ElementList(entry="int", type=Integer.class, required=false, inline=true),
         @ElementList(entry="double", type=Double.class, required=false, inline=true)
      })
      private List<Object> list;
   }
   
   public void testFloatingText() throws Exception {
      Persister persister = new Persister();
      Example test = persister.read(Example.class, SOURCE);
      
      assertNotNull(test.list);
      assertEquals(test.list.get(0), "An int follows ");
      assertEquals(test.list.get(1).getClass(), Integer.class);
      assertEquals(test.list.get(1), 12);
      assertEquals(test.list.get(2), " A double follows ");
      assertEquals(test.list.get(3).getClass(), Double.class);
      assertEquals(test.list.get(3), 33.3);
      
      persister.write(test, System.out);
      validate(persister, test);
   }
   
   public void testFloatingTextElementError() throws Exception {
      Persister persister = new Persister();
      boolean failure = false;
      
      try {
         persister.read(ExampleWithElementsIsAnError.class, SOURCE);
      } catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Elements used with @Text and @ElementListUnion is illegal", failure);
   }
   
   public void testFloatingTextTextError() throws Exception {
      Persister persister = new Persister();
      boolean failure = false;
      
      try {
         persister.read(ExampleWithTextIsAnError.class, SOURCE);
      } catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Text used with @Text and @ElementListUnion is illegal", failure);
   }
   
   public void testFloatingTextWithAttributes() throws Exception {
      Persister persister = new Persister();
      ExampleWithAttributeNotAnError test = persister.read(ExampleWithAttributeNotAnError.class, SOURCE_WITH_ATTRIBUTES);
      
      assertNotNull(test.list);
      assertNotNull(test.a, "A");
      assertNotNull(test.b, "B");
      assertEquals(test.list.get(0), "An int follows ");
      assertEquals(test.list.get(1).getClass(), Integer.class);
      assertEquals(test.list.get(1), 12);
      assertEquals(test.list.get(2), " A double follows ");
      assertEquals(test.list.get(3).getClass(), Double.class);
      assertEquals(test.list.get(3), 33.3);
      
      persister.write(test, System.out);
      validate(persister, test);
   }
   
   public void testFloatingTextPathError() throws Exception {
      Persister persister = new Persister();
      boolean failure = false;
      
      try {
         persister.read(ExampleWithPathAnError.class, SOURCE);
      } catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Path used with @Text and @ElementListUnion is illegal", failure);
   }
   
   public void testFloatingTextStringElementError() throws Exception {
      Persister persister = new Persister();
      boolean failure = false;
      
      try {
         persister.read(ExampleWithStringElementInAnError.class, SOURCE_WITH_ATTRIBUTES);
      } catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("String entries used with @Text and @ElementListUnion is illegal", failure);
   }
   
   public void testTextWithPath() throws Exception {
      Persister persister = new Persister();
      ExampleWithPath test = persister.read(ExampleWithPath.class, SOURCE_WITH_CHILD);
      
      assertNotNull(test.list);
      assertEquals(test.list.get(0), "An int follows ");
      assertEquals(test.list.get(1).getClass(), Integer.class);
      assertEquals(test.list.get(1), 12);
      assertEquals(test.list.get(2), " A double follows ");
      assertEquals(test.list.get(3).getClass(), Double.class);
      assertEquals(test.list.get(3), 33.3);
      
      persister.write(test, System.out);
      validate(persister, test);
   }
}
