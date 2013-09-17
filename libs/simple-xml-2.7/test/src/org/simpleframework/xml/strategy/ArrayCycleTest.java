package org.simpleframework.xml.strategy;

import java.io.StringWriter;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.CycleStrategy;

import org.simpleframework.xml.ValidationTestCase;

public class ArrayCycleTest extends ValidationTestCase {
   
   private static final String SOURCE =
   "<?xml version=\"1.0\"?>\n"+
   "<root id='main'>\n"+
   "   <one length='5' id='numbers'>\n\r"+
   "      <text value='entry one'/>  \n\r"+
   "      <text value='entry two'/>  \n\r"+
   "      <text value='entry three'/>  \n\r"+
   "      <text value='entry four'/>  \n\r"+
   "      <text value='entry five'/>  \n\r"+
   "   </one>\n\r"+
   "   <two ref='numbers'/>\n"+
   "   <three length='3'>\n" +
   "      <text value='tom'/>  \n\r"+
   "      <text value='dick'/>  \n\r"+
   "      <text value='harry'/>  \n\r"+
   "   </three>\n"+
   "   <example ref='main'/>\n"+
   "</root>";
   
   private static final String NESTED =
   "<?xml version=\"1.0\"?>\n"+
   "<root id='main'>\n"+
   "   <array id='array' length='2'>\n" + // NestedExample.array : Value[]
   "      <entry>  \n\r"+
   "         <list ref='array'/>\n"+  // Value.list : Value[] -> NestedArray.array : Value[]
   "      </entry>\r\n"+ 
   "      <entry id='text'>\n"+
   "         <list length='3' id='foo'>\n"+  // Value.list : Value[]
   "            <entry name='blah' class='org.simpleframework.xml.strategy.ArrayCycleTest$TextValue'>\n"+ // Value.list[0] : Value 
   "              <text>Some text</text>\n"+  // TextExample.text : String
   "              <list ref='foo'/>\n"+ // TextExample.list : Value[]
   "            </entry>\n"+
   "            <entry ref='text'/>\n"+ // Value.list[1] : Value
   "            <entry class='org.simpleframework.xml.strategy.ArrayCycleTest$ElementValue'>\n"+ // Value.list[2] : Value
   "               <element><![CDATA[Example element text]]></element>\n"+ // ElementExample.element : String
   "            </entry>\n"+
   "         </list>\n"+
   "      </entry> \n\t\n"+
   "   </array>\n"+
   "</root>";
   
   private static final String PROMOTE =
   "<value>\n"+
   "   <list length='1' class='org.simpleframework.xml.strategy.ArrayCycleTest$ElementValue'>\n"+
   "      <entry class='org.simpleframework.xml.strategy.ArrayCycleTest$ElementValue'>\n"+
   "         <element>Example text</element>\n"+
   "      </entry>\n"+
   "   </list>\n"+
   "</value>\n";

   @Root(name="root")
   private static class ArrayCycleExample {

      @ElementArray(name="one", entry="entry")           
      public Entry[] one;      
      
      @ElementArray(name="two", entry="entry")           
      public Entry[] two;
      
      @ElementArray(name="three", entry="entry")           
      public Entry[] three;
      
      @Element(name="example")
      public ArrayCycleExample example;
   }

   @Root(name="text") 
   private static class Entry {

      @Attribute(name="value")
      public String value;

      public Entry() {
         super();              
      }

      public Entry(String value) {
         this.value = value;              
      }
   }   
   
   @Root(name="root")
   private static class NestedArrayCycleExample {
      
      @ElementArray(name="array", entry="entry")
      public Value[] array;
   }
   
   @Root(name="value")
   private static class Value {
      
      @ElementArray(name="list", entry="entry", required=false)
      private Value[] list;
   }
   
   private static class TextValue extends Value {
      
      @Attribute(name="name")
      private String name;
      
      @Element(name="text")
      private String text;
   }
   
   private static class ElementValue extends Value {
      
      @Element(name="element")
      private String element;
   }
   
   private Persister persister;
   
   public void setUp() throws Exception {
      persister = new Persister(new CycleStrategy("id", "ref"));
   }
   
   public void testCycle() throws Exception {
      ArrayCycleExample example = persister.read(ArrayCycleExample.class, SOURCE);
      
      assertEquals(example.one.length, 5);
      assertEquals(example.one[0].value, "entry one");
      assertEquals(example.one[1].value, "entry two");
      assertEquals(example.one[2].value, "entry three");
      assertEquals(example.one[3].value, "entry four");
      assertEquals(example.one[4].value, "entry five");
      
      assertEquals(example.two.length, 5);
      assertEquals(example.two[0].value, "entry one");
      assertEquals(example.two[1].value, "entry two");
      assertEquals(example.two[2].value, "entry three");
      assertEquals(example.two[3].value, "entry four");
      assertEquals(example.two[4].value, "entry five");
      
      assertEquals(example.three.length, 3);
      assertEquals(example.three[0].value, "tom");
      assertEquals(example.three[1].value, "dick");
      assertEquals(example.three[2].value, "harry");
   
      assertTrue(example.one == example.two);
      assertTrue(example == example.example);
      
      StringWriter out = new StringWriter();
      persister.write(example, out);
      
      example = persister.read(ArrayCycleExample.class, SOURCE);
      
      assertEquals(example.one.length, 5);
      assertEquals(example.one[0].value, "entry one");
      assertEquals(example.one[1].value, "entry two");
      assertEquals(example.one[2].value, "entry three");
      assertEquals(example.one[3].value, "entry four");
      assertEquals(example.one[4].value, "entry five");
      
      assertEquals(example.two.length, 5);
      assertEquals(example.two[0].value, "entry one");
      assertEquals(example.two[1].value, "entry two");
      assertEquals(example.two[2].value, "entry three");
      assertEquals(example.two[3].value, "entry four");
      assertEquals(example.two[4].value, "entry five");
      
      assertEquals(example.three.length, 3);
      assertEquals(example.three[0].value, "tom");
      assertEquals(example.three[1].value, "dick");
      assertEquals(example.three[2].value, "harry");
   
      assertTrue(example.one == example.two);
      assertTrue(example == example.example);
      
      validate(example, persister);
   }
   
   public void testNestedExample() throws Exception {
      NestedArrayCycleExample root = persister.read(NestedArrayCycleExample.class, NESTED);
      
      assertEquals(root.array.length, 2);
      assertTrue(root.array[0].list == root.array);
      assertTrue(root.array[1].list[0] instanceof TextValue);
      assertTrue(root.array[1].list == root.array[1].list[0].list);
      
      assertTrue(root.array[1] == root.array[1].list[1]);
      assertTrue(root.array[1].list == root.array[1].list[0].list);
      assertTrue(root.array[1].list[2] instanceof ElementValue);
      
      ElementValue element = (ElementValue) root.array[1].list[2];
      TextValue text = (TextValue) root.array[1].list[0];
      
      assertEquals(element.element, "Example element text");
      assertEquals(text.name, "blah");
      assertEquals(text.text, "Some text");
      
      validate(root, persister);
      
      StringWriter out = new StringWriter();
      persister.write(root, out);
      
      // Ensure references survive serialization
      root = persister.read(NestedArrayCycleExample.class, out.toString());
      
      assertEquals(root.array.length, 2);
      assertTrue(root.array[0].list == root.array);
      assertTrue(root.array[1].list[0] instanceof TextValue);
      assertTrue(root.array[1].list == root.array[1].list[0].list);
      
      assertTrue(root.array[1] == root.array[1].list[1]);
      assertTrue(root.array[1].list == root.array[1].list[0].list);
      assertTrue(root.array[1].list[2] instanceof ElementValue);
      
      element = (ElementValue) root.array[1].list[2];
      text = (TextValue) root.array[1].list[0];
      
      assertEquals(element.element, "Example element text");
      assertEquals(text.name, "blah");
      assertEquals(text.text, "Some text");
   }
   
   public void testPromotion() throws Exception {
      Value example = persister.read(Value.class, PROMOTE);
      
      assertEquals(example.list.length, 1);
      assertTrue(example.list instanceof ElementValue[]);
      
      ElementValue value = (ElementValue) example.list[0];
      
      assertEquals(value.element, "Example text");
      
      validate(example, persister);
   }
}






















