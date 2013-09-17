package org.simpleframework.xml.core;

import java.util.List;
import java.util.Map;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementArray;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;

public class DefaultEmptyTest extends ValidationTestCase {
   
   private static final String SOURCE = 
   "<defaultExample name='test'>\n" +
   "  <text>some text</text>\n"+
   "</defaultExample>";
   
   @Root
   private static class DefaultExample  {
      
      @ElementList(empty=false, required=false)
      private List<String> stringList;
      
      @ElementMap(empty=false, required=false)
      private Map<String, String> stringMap;
      
      @ElementArray(empty=false, required=false)
      private String[] stringArray;
      
      @Attribute
      private String name;
      
      @Element
      private String text;
      
      public DefaultExample() {
         super();
      }
      
      public DefaultExample(String name, String text) {
         this.name = name;
         this.text = text;
      }
   }
   
   public void testDefaults() throws Exception {
      Persister persister = new Persister();
      DefaultExample example = persister.read(DefaultExample.class, SOURCE);
    
      assertEquals(example.name, "test");
      assertEquals(example.text, "some text");
      assertNotNull(example.stringList);
      assertNotNull(example.stringMap);
      assertNotNull(example.stringArray);
      
      persister.write(example, System.out);
      
      validate(persister, example);
      
      persister.write(new DefaultExample("name", "example text"), System.out);
   }

}
