package org.simpleframework.xml.core;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.DefaultType;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class NotRequiredConstructorInjectionTest extends ValidationTestCase{
   
   private static final String SOURCE =
   "<exampleNotRequired value='value'/>";
   
   private static final String EXTRA =
   "<exampleNotRequired value='value'>"+
   "  <extra>extra string</extra>"+
   "</exampleNotRequired>";
      
   @Root
   private static class ExampleNotRequired {
      private final String name;
      private final String value;
      public ExampleNotRequired(@Element(name="name", required=false) String name, @Attribute(name="value") String value) {
         this.name = name;
         this.value = value;
      }
      @Attribute
      public String getValue() { // VERY VERY STRANGE STUFF, ExampleNotRequiredByDefault ends up declaring this method if its class scope is public
         return value;
      }
      @Element(required=false)
      public String getName() { // VERY VERY STRANGE STUFF, ExampleNotRequiredByDefault ends up declaring this method if its class scope is public
         return name;
      }
   }
   
   @Default(value=DefaultType.PROPERTY, required=false)
   private static class ExampleNotRequiredByDefault extends ExampleNotRequired {
      private final String extra;
      public ExampleNotRequiredByDefault(@Element(name="name", required=false) String name, @Attribute(name="value") String value, @Element(name="extra", required=false) String extra) {
         super(name, value);
         this.extra = extra;
      }
      public String getExtra() {
         return extra;
      }
   }
   
   public void testInjection() throws Exception {
      ExampleNotRequired example = new Persister().read(ExampleNotRequired.class, SOURCE);
      assertEquals(example.getName(), null);
      assertEquals(example.getValue(), "value");
   }
   
   public void testInheritance() throws Exception {
      ExampleNotRequiredByDefault example = new Persister().read(ExampleNotRequiredByDefault.class, SOURCE);
      assertEquals(example.getName(), null);
      assertEquals(example.getValue(), "value");
      assertEquals(example.getExtra(), null);
      
      ExampleNotRequiredByDefault exampleExtra = new Persister().read(ExampleNotRequiredByDefault.class, EXTRA);
      assertEquals(exampleExtra.getName(), null);
      assertEquals(exampleExtra.getValue(), "value");
      assertEquals(exampleExtra.getExtra(), "extra string");
   }
}
