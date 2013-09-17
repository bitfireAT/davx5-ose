package org.simpleframework.xml.core;

import java.lang.annotation.Annotation;

import junit.framework.TestCase;

import org.simpleframework.xml.strategy.Type;

public class KeyBuilderTest extends TestCase {

   public void testBuilder() throws Exception {
      Label elementLabel = new MockLabel(false, false, "a", "b");
      KeyBuilder elementBuilder = new KeyBuilder(elementLabel);
      Object elementKey = elementBuilder.getKey();
      
      assertEquals(elementKey.toString(), "a>b>");
      
      Label attributeLabel = new MockLabel(true, false, "a", "b");
      KeyBuilder attributeBuilder = new KeyBuilder(attributeLabel);
      Object attributeKey = attributeBuilder.getKey();
      
      assertEquals(attributeKey.toString(), "a>b>");
      assertEquals(attributeKey.toString(), elementKey.toString());
      assertFalse(attributeKey.equals(elementKey));
      assertFalse(elementKey.equals(attributeKey));
   }
   
   public class MockLabel implements Label {
      
      private String[] list;
      private boolean attribute;
      private boolean text;
      
      public MockLabel(boolean attribute, boolean text, String... paths) {
         this.attribute = attribute;
         this.list = paths;
         this.text= text;
      }

      public Decorator getDecorator() throws Exception {
         return null;
      }

      public Type getType(Class type) throws Exception {
         return null;
      }

      public Label getLabel(Class type) throws Exception {
         return null;
      }

      public String[] getNames() throws Exception {
         return null;
      }

      public String[] getPaths() throws Exception {
         return list;
      }

      public Object getEmpty(Context context) throws Exception {
         return null;
      }

      public Converter getConverter(Context context) throws Exception {
         return null;
      }

      public String getName() throws Exception {
         return null;
      }

      public String getPath() throws Exception {
         return null;
      }

      public Expression getExpression() throws Exception {
         return null;
      }

      public Type getDependent() throws Exception {
         return null;
      }

      public String getEntry() throws Exception {
         return null;
      }

      public Object getKey() throws Exception {
         return null;
      }

      public Annotation getAnnotation() {
         return null;
      }

      public Contact getContact() {
         return null;
      }

      public Class getType() {
         return null;
      }

      public String getOverride() {
         return null;
      }

      public boolean isData() {
         return false;
      }

      public boolean isRequired() {
         return false;
      }

      public boolean isAttribute() {
         return attribute;
      }

      public boolean isCollection() {
         return false;
      }

      public boolean isInline() {
         return false;
      }

      public boolean isText() {
         return text;
      }

      public boolean isUnion() {
         return false;
      }

      public boolean isTextList() {
         return false;
      }
      
   }
}
