package org.simpleframework.xml.core;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.transform.Matcher;
import org.simpleframework.xml.transform.Transform;

public class LiteralTest extends ValidationTestCase {

   @Root(strict=false)
   private static class LiteralExample {
      
      @Attribute
      private String name;
      
      @Attribute
      private String key;
      
      @Text(required=false)
      private final Literal literal = new Literal(
      "<literal id='a' value='a'>\n"+
      "   <child>some example text</child>\n"+
      "</literal>\n");
      
      private LiteralExample() {
         super();
      }
      
      public LiteralExample(String name, String key) {
         this.name = name;
         this.key = key;
      }
      
      public String getName() {
         return name;
      }
   }
   
   private static class LiteralMatcher implements Matcher {
      
      public Transform match(Class type) {
         if(type == Literal.class) {
            return new LiteralTransform();
         }
         return null;
      }
   }
   
   private static class LiteralTransform implements Transform {

      public Object read(String value) throws Exception {
         return new Literal(value);
      }

      public String write(Object value) throws Exception {
         return value.toString();
      }
   }
   
   private static class Literal {
      
      private final String content;
      
      public Literal(String content) {
         this.content = content;
      }
      
      public String toString() {
         return content;
      }
   }
   
   
   public void testLiteral() throws Exception {
      Matcher matcher = new LiteralMatcher();
      Persister persister = new Persister(matcher);
      LiteralExample example = new LiteralExample("name", "key");
      
      persister.write(example, System.out);
   }
}
