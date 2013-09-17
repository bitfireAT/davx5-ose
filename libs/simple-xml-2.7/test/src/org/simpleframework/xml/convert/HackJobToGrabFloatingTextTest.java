package org.simpleframework.xml.convert;

import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeBuilder;
import org.simpleframework.xml.stream.OutputNode;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class HackJobToGrabFloatingTextTest extends ValidationTestCase {

   private static final String SOURCE =
   "<element1>\n"+
   "   some inner text\n"+
   "   <child1>11</child1>\n"+
   "   <child2>True</child2>\n" +
   "</element1>";
   
   private static class SomethingConverter implements Converter<Something> {

      public Something read(InputNode node) throws Exception {
         Object source = node.getSource();
         Element element = (Element)source;
         NodeList elements = element.getChildNodes();
         String child1 = null;
         String child2 = null;
         String text = "";
         for(int i = 0; i < elements.getLength(); i++) {
            Node next = elements.item(i);
            if(next.getNodeType() == Node.TEXT_NODE) {
               text += next.getNodeValue();
            }
            if(next.getNodeType() == Node.ELEMENT_NODE) {
               if(next.getNodeName().equals("child1")) {
                  child1 = next.getTextContent();
               }
               if(next.getNodeName().equals("child2")) {
                  child2 = next.getTextContent();
               }
            }
         }
         return new Something(child1, child2, text.trim());
      }

      public void write(OutputNode node, Something car) throws Exception {
         // Free text not supported!!
      }
   }
   
   @Root(name = "element1")
   @Convert(SomethingConverter.class)
   public static class Something {
       public String child1;
       public String child2;
       public String text;
       public Something(String child1, String child2, String text) {
          this.child1 = child1;
          this.child2 = child2;
          this.text = text;
       }
   }
   
   public void testHackJob() throws Exception {
      Class<?> type = Class.forName("org.simpleframework.xml.stream.DocumentProvider");
      Constructor<?> constructor = type.getDeclaredConstructor();
      constructor.setAccessible(true);
      Object value = constructor.newInstance();
      Field[] fields = NodeBuilder.class.getDeclaredFields();
      
      for(Field field : fields) {
         if(field.getName().equalsIgnoreCase("provider")) {
            field.setAccessible(true);
            field.set(null, value);
         }
      }
      StringReader reader = new StringReader(SOURCE);
      InputNode source = NodeBuilder.read(reader);
      AnnotationStrategy strategy = new AnnotationStrategy();
      Persister persister = new Persister(strategy);
      Something something = persister.read(Something.class, source);
      
      assertNotNull(something);
      assertEquals(something.text, "some inner text");
      assertEquals(something.child1, "11");
      assertEquals(something.child2, "True");
   }
}
