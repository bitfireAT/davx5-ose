package org.simpleframework.xml.core;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.stream.CamelCaseStyle;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.Style;

public class ConstructorInjectionTest extends ValidationTestCase {
   
   private static final String SOURCE = 
   "<example number='32'>"+
   "  <integer>12</integer>"+
   "  <string>text</string>"+
   "</example>";
   
   private static final String PARTIAL = 
   "<example>"+
   "  <integer>12</integer>"+
   "  <string>text</string>"+
   "</example>";
   
   private static final String BARE = 
   "<example>"+
   "  <integer>12</integer>"+
   "</example>";
   
   private static final String ARRAY =
   "<ExampleArray>"+
   "   <Array length='5'>\n\r"+
   "      <String>entry one</String>  \n\r"+
   "      <String>entry two</String>  \n\r"+
   "      <String>entry three</String>  \n\r"+
   "      <String>entry four</String>  \n\r"+
   "      <String>entry five</String>  \n\r"+
   "   </Array>\n\r"+
   "</ExampleArray>";
   
   
   @Root
   private static class Example {
      
      @Element
      private int integer;   
      
      @Element(required=false) 
      private String string;
      
      @Attribute(name="number", required=false) 
      private long number;
      
      public Example(@Element(name="integer") int integer){
         this.integer = integer;
      }
      public Example(@Element(name="integer") int integer, @Element(name="string", required=false) String string, @Attribute(name="number", required=false) long number){
         this.integer = integer;
         this.string = string;
         this.number = number;
      }
      public Example(@Element(name="integer") int integer, @Element(name="string", required=false) String string){
         this.integer = integer;
         this.string = string;
      }
   }
   
   @Root
   private static class ArrayExample {
      
      @ElementArray(name="array")
      private final String[] array;
      
      public ArrayExample(@ElementArray(name="array") String[] array) {
         this.array = array;
      }
      
      public String[] getArray() {
         return array;
      }
   }
   
   public void testConstructor() throws Exception {      
      Persister persister = new Persister();
      Example example = persister.read(Example.class, SOURCE);
      
      assertEquals(example.integer, 12);
      assertEquals(example.number, 32);
      assertEquals(example.string, "text"); 
      
      validate(persister, example);
   }
   
   public void testPartialConstructor() throws Exception {      
      Persister persister = new Persister();
      Example example = persister.read(Example.class, PARTIAL);
      
      assertEquals(example.integer, 12);
      assertEquals(example.number, 0);
      assertEquals(example.string, "text"); 
      
      validate(persister, example);
   }
   
   public void testBareConstructor() throws Exception {      
      Persister persister = new Persister();
      Example example = persister.read(Example.class, BARE);
      
      assertEquals(example.integer, 12);
      assertEquals(example.number, 0);
      assertEquals(example.string, null); 
      
      validate(persister, example);
   }
   
   public void testArrayExample() throws Exception {   
      Style style = new CamelCaseStyle();
      Format format = new Format(style);
      Persister persister = new Persister(format);
      ArrayExample example = persister.read(ArrayExample.class, ARRAY);
      
      assertEquals(example.getArray().length, 5);
      assertEquals(example.getArray()[0], "entry one");
      assertEquals(example.getArray()[1], "entry two");
      assertEquals(example.getArray()[2], "entry three");
      assertEquals(example.getArray()[3], "entry four");
      assertEquals(example.getArray()[4], "entry five");
      
      validate(persister, example);
   }
}
