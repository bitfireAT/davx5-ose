package org.simpleframework.xml.strategy;

import java.io.StringWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Constructor;
import java.util.Map;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeMap;
import org.simpleframework.xml.stream.OutputNode;

public class AnnotationConverterTest extends ValidationTestCase {

   @Retention(RetentionPolicy.RUNTIME)
   private static @interface Convert{
      public Class<? extends Converter> value();
   }
   
   private static interface Converter<T> {
      public T read(InputNode node) throws Exception;
      public void write(OutputNode node, T value) throws Exception;
   }
   
   private static class CowConverter implements Converter<Cow> {
      public Cow read(InputNode node) throws Exception {
         String name = node.getAttribute("name").getValue();
         String age = node.getAttribute("age").getValue();
         return new Cow(name, Integer.parseInt(age));
      }
      public void write(OutputNode node, Cow cow) throws Exception {
         node.setAttribute("name", cow.getName());
         node.setAttribute("age", String.valueOf(cow.getAge()));
         node.setAttribute("legs", String.valueOf(cow.getLegs()));
      }
   }
   
   private static class ChickenConverter implements Converter<Chicken> {
      public Chicken read(InputNode node) throws Exception {
         String name = node.getAttribute("name").getValue();
         String age = node.getAttribute("age").getValue();
         return new Chicken(name, Integer.parseInt(age));
      }
      public void write(OutputNode node, Chicken chicken) throws Exception {
         node.setAttribute("name", chicken.getName());
         node.setAttribute("age", String.valueOf(chicken.getAge()));
         node.setAttribute("legs", String.valueOf(chicken.getLegs()));
      }
   }
   
   private static class Animal {
      private final String name;
      private final int age;
      private final int legs;
      public Animal(String name, int age, int legs) {
         this.name = name;
         this.legs = legs;
         this.age = age;
      }
      public String getName() {
         return name;
      }
      public int getAge(){
         return age;
      }      
      public int getLegs() {
         return legs;
      }
   }
   
   private static class Chicken extends Animal {
      public Chicken(String name, int age) {
         super(name, age, 2);
      }
   }
   
   private static class Cow extends Animal {
      public Cow(String name, int age) {
         super(name, age, 4);
      }
   }
   
   private static class AnnotationStrategy implements Strategy {
      private final Strategy strategy;
      public AnnotationStrategy(Strategy strategy) {
         this.strategy = strategy;
      }
      public Value read(Type type, NodeMap<InputNode> node, Map map) throws Exception {
         Value value = strategy.read(type, node, map);
         Convert convert = type.getAnnotation(Convert.class);
         InputNode parent = node.getNode();
         
         if(convert != null) {
            Class<? extends Converter> converterClass = convert.value();
            Constructor<? extends Converter> converterConstructor = converterClass.getDeclaredConstructor();
            
            if(!converterConstructor.isAccessible()) {
               converterConstructor.setAccessible(true);
            }
            Converter converter = converterConstructor.newInstance();
            Object result = converter.read(parent);
            return new Wrapper(result);
         }
         return value;
      }
      public boolean write(Type type, Object value, NodeMap<OutputNode> node, Map map) throws Exception {
         Convert convert = type.getAnnotation(Convert.class);
         OutputNode parent = node.getNode();
         
         if(convert != null) {
            Class<? extends Converter> converterClass = convert.value();
            Constructor<? extends Converter> converterConstructor = converterClass.getDeclaredConstructor();
            
            if(!converterConstructor.isAccessible()) {
               converterConstructor.setAccessible(true);
            }
            Converter converter = converterConstructor.newInstance();
            converter.write(parent, value);
            return true;
         }
         return strategy.write(type, value, node, map);
      }
   }  
   
   public static class Wrapper implements Value {
      private Object data;
      public Wrapper(Object data){
         this.data = data;
      }
      public int getLength() {
         return 0;
      }
      public Class getType() {
         return data.getClass();
      }
      public Object getValue() {
         return data;
      }
      public boolean isReference() {
         return true;
      }
      public void setValue(Object data) {
         this.data = data;
      }
   }
   
   private static final String SOURCE =
   "<farmExample>"+
   "    <chicken name='Hen' age='1' legs='2'/>"+
   "    <cow name='Bull' age='4' legs='4'/>"+
   "</farmExample>";
   
   @Root
   private static class FarmExample {
      @Element
      @Convert(ChickenConverter.class)
      private Chicken chicken;
      @Element
      @Convert(CowConverter.class)
      private Cow cow;
      
      public FarmExample(@Element(name="chicken") Chicken chicken, @Element(name="cow") Cow cow) {
         this.chicken = chicken;
         this.cow = cow;
      }
      public Chicken getChicken() {
         return chicken;
      }
      public Cow getCow() {
         return cow;
      }
   }
   
   public void testAnnotationConversion() throws Exception {
      Strategy strategy = new TreeStrategy();
      Strategy converter = new AnnotationStrategy(strategy);
      Serializer serializer = new Persister(converter);
      StringWriter writer = new StringWriter();
      FarmExample example = serializer.read(FarmExample.class, SOURCE);
      
      assertEquals(example.getCow().getName(), "Bull");
      assertEquals(example.getCow().getAge(), 4);
      assertEquals(example.getCow().getLegs(), 4);
      assertEquals(example.getChicken().getName(), "Hen");
      assertEquals(example.getChicken().getAge(), 1);
      assertEquals(example.getChicken().getLegs(), 2);
      
      serializer.write(example, System.out);
      serializer.write(example, writer);
      
      String text = writer.toString();
      
      assertElementHasAttribute(text, "/farmExample/chicken", "name", "Hen");
      assertElementHasAttribute(text, "/farmExample/chicken", "age", "1");
      assertElementHasAttribute(text, "/farmExample/chicken", "legs", "2");
      assertElementHasAttribute(text, "/farmExample/cow", "name", "Bull");
      assertElementHasAttribute(text, "/farmExample/cow", "age", "4");
      assertElementHasAttribute(text, "/farmExample/cow", "legs", "4");


   }
}
