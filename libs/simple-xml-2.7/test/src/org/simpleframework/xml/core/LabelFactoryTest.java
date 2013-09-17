package org.simpleframework.xml.core;

import java.util.List;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.ElementUnion;

public class LabelFactoryTest extends TestCase {
   
   private static class Example {
      @ElementUnion({
         @Element(name="a", type=String.class),
         @Element(name="b", type=Integer.class)
      })
      private Object value;
   }
   
   private static class ExampleList {
      @ElementListUnion({
         @ElementList(name="a", type=String.class),
         @ElementList(name="b", type=Integer.class)
      })
      private List<Object> value;
   }

   public void testUnion() throws Exception {
      FieldScanner scanner = new FieldScanner(new DetailScanner(Example.class), new Support());
      Support support = new Support();
      Contact contact = scanner.get(0);
      Element element = ((ElementUnion)contact.getAnnotation()).value()[0];
      Label unionLabel = support.getLabel(contact, contact.getAnnotation());
      Label elementLabel = support.getLabel(contact, element);
      
      assertEquals(unionLabel.getName(), "a");
      assertEquals(elementLabel.getName(), "a");
   }
   
   public void testUnionList() throws Exception {
      FieldScanner scanner = new FieldScanner(new DetailScanner(ExampleList.class), new Support());
      Support support = new Support();
      Contact contact = scanner.get(0);
      ElementList element = ((ElementListUnion)contact.getAnnotation()).value()[0];
      Label unionLabel = support.getLabel(contact, contact.getAnnotation());
      Label elementLabel = support.getLabel(contact, element);
      
      assertEquals(unionLabel.getName(), "a");
      assertEquals(elementLabel.getName(), "a");
   }
}
