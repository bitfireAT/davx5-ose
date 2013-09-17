package org.simpleframework.xml.convert;

import java.io.StringWriter;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.HyphenStyle;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;
import org.simpleframework.xml.stream.Style;

public class CombinedStrategyTest extends ValidationTestCase {

   private static class Item {
      private int value;
      public Item(int value) {
         this.value = value;
      }
      public int getValue(){
         return value;
      }
   }
   
   @Root
   @Convert(ExtendedItemConverter.class)
   private static class ExtendedItem extends Item {
      public ExtendedItem(int value) {
         super(value);
      }
   }
   
   private static class AnnotationItemConverter implements Converter<Item> {
      public Item read(InputNode node) throws Exception {
         return new Item(Integer.parseInt(node.getAttribute("value").getValue()));
      }
      public void write(OutputNode node, Item value) throws Exception {
         node.setAttribute("value", String.valueOf(value.getValue()));
         node.setAttribute("type", getClass().getName());
      }  
   }
   
   private static class RegistryItemConverter implements Converter<Item> {
      public Item read(InputNode node) throws Exception {
         return new Item(Integer.parseInt(node.getNext().getValue()));
      }
      public void write(OutputNode node, Item value) throws Exception {
         node.getChild("value").setValue(String.valueOf(value.getValue()));
         node.getChild("type").setValue(getClass().getName());
      }
   }
   
   private static class ExtendedItemConverter implements Converter<ExtendedItem> {
      public ExtendedItem read(InputNode node) throws Exception {
         return new ExtendedItem(Integer.parseInt(node.getAttribute("value").getValue()));
      }
      public void write(OutputNode node, ExtendedItem value) throws Exception {
         node.setAttribute("value", String.valueOf(value.getValue()));
         node.setAttribute("type", getClass().getName());
      } 
   }
   
   @Root
   private static class CombinationExample {
      @Element
      private Item item; // handled by the registry
      @Element
      @Convert(AnnotationItemConverter.class) // handled by annotation
      private Item overriddenItem;
      @Element
      private Item extendedItem; // handled by class annotation
      public CombinationExample(int item, int overriddenItem, int extendedItem) {
         this.item = new Item(item);
         this.overriddenItem = new Item(overriddenItem);
         this.extendedItem = new ExtendedItem(extendedItem);
      }
      public Item getItem(){
         return item;
      }
      public Item getOverriddenItem(){
         return overriddenItem;
      }
      public Item getExtendedItem() {
         return extendedItem;
      }
   }
   
   public void testCombinedStrategy() throws Exception {
      Registry registry = new Registry();
      AnnotationStrategy annotationStrategy = new AnnotationStrategy();
      RegistryStrategy registryStrategy = new RegistryStrategy(registry, annotationStrategy);
      Persister persister = new Persister(registryStrategy);
      CombinationExample example = new CombinationExample(1, 2, 3);
      StringWriter writer = new StringWriter();
      
      registry.bind(Item.class, RegistryItemConverter.class);
      persister.write(example, writer);
      
      String text = writer.toString();
      System.out.println(text);
      
      assertElementExists(text, "/combinationExample/item/value");
      assertElementHasValue(text, "/combinationExample/item/value", "1");
      assertElementHasValue(text, "/combinationExample/item/type", RegistryItemConverter.class.getName());
      assertElementExists(text, "/combinationExample/overriddenItem");
      assertElementHasAttribute(text, "/combinationExample/overriddenItem", "value", "2");
      assertElementHasAttribute(text, "/combinationExample/overriddenItem", "type", AnnotationItemConverter.class.getName());
      assertElementExists(text, "/combinationExample/extendedItem");
      assertElementHasAttribute(text, "/combinationExample/extendedItem", "value", "3");
      assertElementHasAttribute(text, "/combinationExample/extendedItem", "type", ExtendedItemConverter.class.getName());
   }
   
   public void testCombinationStrategyWithStyle() throws Exception {
      Registry registry = new Registry();
      AnnotationStrategy annotationStrategy = new AnnotationStrategy();
      RegistryStrategy registryStrategy = new RegistryStrategy(registry, annotationStrategy);
      Style style = new HyphenStyle();
      Format format = new Format(style);
      Persister persister = new Persister(registryStrategy, format);
      CombinationExample example = new CombinationExample(1, 2, 3);
      StringWriter writer = new StringWriter();
      
      registry.bind(Item.class, RegistryItemConverter.class);
      persister.write(example, writer);
      
      String text = writer.toString();
      System.out.println(text);
      
      assertElementExists(text, "/combination-example/item/value");
      assertElementHasValue(text, "/combination-example/item/value", "1");
      assertElementHasValue(text, "/combination-example/item/type", RegistryItemConverter.class.getName());
      assertElementExists(text, "/combination-example/overridden-item");
      assertElementHasAttribute(text, "/combination-example/overridden-item", "value", "2");
      assertElementHasAttribute(text, "/combination-example/overridden-item", "type", AnnotationItemConverter.class.getName());
      assertElementExists(text, "/combination-example/extended-item");
      assertElementHasAttribute(text, "/combination-example/extended-item", "value", "3");
      assertElementHasAttribute(text, "/combination-example/extended-item", "type", ExtendedItemConverter.class.getName());      
   }
}
