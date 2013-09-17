package org.simpleframework.xml.transform;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.CycleStrategy;

public class PrimitiveArrayTransformTest extends ValidationTestCase {
   
   @Root
   public static class IntegerArrayExample {
      
      @Attribute(required=false)     
      private int[] attribute;
      
      @Element(required=false)
      private int[] element;
      
      @ElementList
      private List<int[]> list;
      
      @ElementArray
      private int[][] array;
      
      @Element
      private NonPrimitive test;
      
      @ElementList
      private List<NonPrimitive> testList;
      
      @ElementArray
      private NonPrimitive[] testArray;
      
      public IntegerArrayExample() {
         super();
      }
      
      public IntegerArrayExample(int[] list) {
         this.attribute = list;
         this.element = list;         
         this.list = new ArrayList<int[]>();
         this.list.add(list);
         this.list.add(list);
         this.array = new int[1][];
         this.array[0] = list;
         this.testList = new ArrayList<NonPrimitive>();
         this.testList.add(null);
         this.testList.add(null);
         this.test = new NonPrimitive();
         this.testArray = new NonPrimitive[1];
      }   
   }
   
   @Root
   private static class NonPrimitive {
      
      @Attribute
      private String value = "text";
   }
   
   public void testRead() throws Exception {    
      ArrayTransform transform = new ArrayTransform(new IntegerTransform(), int.class);
      int[] list = (int[])transform.read("1,2,3,4");     
 
      assertEquals(1, list[0]);
      assertEquals(2, list[1]);
      assertEquals(3, list[2]);
      assertEquals(4, list[3]);

      list = (int[])transform.read("  123 ,\t\n "+
                                   "1\n\r," +
                                   "100, 23, \t32,\t 0\n,\n"+
                                   "3\n\t");

      assertEquals(123, list[0]);
      assertEquals(1, list[1]);
      assertEquals(100, list[2]);
      assertEquals(23, list[3]);
      assertEquals(32, list[4]);
      assertEquals(0, list[5]);
      assertEquals(3, list[6]);
   }

   public void testWrite() throws Exception {
      ArrayTransform transform = new ArrayTransform(new IntegerTransform(), int.class);
      String value = transform.write(new int[] { 1, 2, 3, 4});

      assertEquals(value, "1, 2, 3, 4");

      value = transform.write(new int[] {1, 0, 3, 4});

      assertEquals(value, "1, 0, 3, 4");
   }
   
   public void testPersistence() throws Exception {
      int[] list = new int[] { 1, 2, 3, 4 };
      Persister persister = new Persister();
      IntegerArrayExample example = new IntegerArrayExample(list);
      StringWriter out = new StringWriter();
      
      assertEquals(example.attribute[0], 1);
      assertEquals(example.attribute[1], 2);
      assertEquals(example.attribute[2], 3);
      assertEquals(example.attribute[3], 4);
      assertEquals(example.element[0], 1);
      assertEquals(example.element[1], 2);
      assertEquals(example.element[2], 3);
      assertEquals(example.element[3], 4);      
      assertEquals(example.list.get(0)[0], 1);
      assertEquals(example.list.get(0)[1], 2);
      assertEquals(example.list.get(0)[2], 3);
      assertEquals(example.list.get(0)[3], 4);
      assertEquals(example.array[0][0], 1);
      assertEquals(example.array[0][1], 2);
      assertEquals(example.array[0][2], 3);
      assertEquals(example.array[0][3], 4);
      
      persister.write(example, out);
      String text = out.toString();
      System.out.println(text);
      
      example = persister.read(IntegerArrayExample.class, text);
            
      assertEquals(example.attribute[0], 1);
      assertEquals(example.attribute[1], 2);
      assertEquals(example.attribute[2], 3);
      assertEquals(example.attribute[3], 4);
      assertEquals(example.element[0], 1);
      assertEquals(example.element[1], 2);
      assertEquals(example.element[2], 3);
      assertEquals(example.element[3], 4);      
      assertEquals(example.list.get(0)[0], 1);
      assertEquals(example.list.get(0)[1], 2);
      assertEquals(example.list.get(0)[2], 3);
      assertEquals(example.list.get(0)[3], 4);
      assertEquals(example.array[0][0], 1);
      assertEquals(example.array[0][1], 2);
      assertEquals(example.array[0][2], 3);
      assertEquals(example.array[0][3], 4);
      
      validate(example, persister);      
      
      example = new IntegerArrayExample(null);
      out = new StringWriter();
      persister.write(example, out);
      text = out.toString();
      
      validate(example, persister);
      
      example = persister.read(IntegerArrayExample.class, text);
      
      assertEquals(example.attribute, null);
      assertEquals(example.element, null);     
      assertEquals(example.list.size(), 0);
      assertEquals(example.array[0], null);
   }
   
   public void testCyclicPersistence() throws Exception {
      int[] list = new int[] { 1, 2, 3, 4 };
      CycleStrategy strategy = new CycleStrategy();
      Persister persister = new Persister(strategy);
      IntegerArrayExample example = new IntegerArrayExample(list);
      StringWriter out = new StringWriter();
      
      assertEquals(example.attribute[0], 1);
      assertEquals(example.attribute[1], 2);
      assertEquals(example.attribute[2], 3);
      assertEquals(example.attribute[3], 4);
      assertEquals(example.element[0], 1);
      assertEquals(example.element[1], 2);
      assertEquals(example.element[2], 3);
      assertEquals(example.element[3], 4);      
      assertEquals(example.list.get(0)[0], 1);
      assertEquals(example.list.get(0)[1], 2);
      assertEquals(example.list.get(0)[2], 3);
      assertEquals(example.list.get(0)[3], 4);
      assertEquals(example.array[0][0], 1);
      assertEquals(example.array[0][1], 2);
      assertEquals(example.array[0][2], 3);
      assertEquals(example.array[0][3], 4);
      
      persister.write(example, out);
      String text = out.toString();
      
      assertElementHasAttribute(text, "/integerArrayExample", "id", "0");
      assertElementHasAttribute(text, "/integerArrayExample/element", "id", "1");
      assertElementHasAttribute(text, "/integerArrayExample/list", "id", "2");
      assertElementHasAttribute(text, "/integerArrayExample/array", "id", "3");
      assertElementHasAttribute(text, "/integerArrayExample/list/int", "reference", "1");
      assertElementHasAttribute(text, "/integerArrayExample/array/int", "reference", "1");      
      assertElementHasValue(text, "/integerArrayExample/element", "1, 2, 3, 4");
      
      validate(example, persister);
      
      example = new IntegerArrayExample(null);
      out = new StringWriter();
      persister.write(example, out);
      text = out.toString();
      
      validate(example, persister);
      
      example = persister.read(IntegerArrayExample.class, text);
      
      assertEquals(example.attribute, null);
      assertEquals(example.element, null);     
      assertEquals(example.list.size(), 0);
      assertEquals(example.array[0], null);
      
      assertElementHasAttribute(text, "/integerArrayExample", "id", "0");
      assertElementHasAttribute(text, "/integerArrayExample/list", "id", "1");
      assertElementHasAttribute(text, "/integerArrayExample/array", "id", "2");
   }
}
