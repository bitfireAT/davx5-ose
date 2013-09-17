package org.simpleframework.xml.convert;

import java.io.StringWriter;
import java.util.List;
import java.util.Vector;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.convert.ExampleConverters.Chicken;
import org.simpleframework.xml.convert.ExampleConverters.ChickenConverter;
import org.simpleframework.xml.convert.ExampleConverters.Cow;
import org.simpleframework.xml.convert.ExampleConverters.CowConverter;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.strategy.Strategy;

public class AnnotationStrategyTest extends ValidationTestCase {

   private static final String SOURCE =
   "<farmExample>"+
   "    <chicken name='Hen' age='1' legs='2'/>"+
   "    <cow name='Bull' age='4' legs='4'/>"+
   "    <farmer age='80'>Old McDonald</farmer>"+
   "    <time>1</time>"+
   "</farmExample>";
   
   @Root
   private static class Farmer {
      
      @Text
      @Namespace(prefix="man", reference="http://www.domain/com/man")
      private String name;
      
      @Attribute
      private int age;
   
      public Farmer() {
         super();
      }
      
      public Farmer(String name, int age) {
         this.name = name;
         this.age = age;
      }
   }
   
   @Root
   private static class FarmExample {
      
      @Element
      @Namespace(prefix="c", reference="http://www.domain.com/test")
      @Convert(ChickenConverter.class)
      private Chicken chicken;
      
      @Element
      @Convert(CowConverter.class)
      private Cow cow;
      
      @Element
      private Farmer farmer;
      
      @ElementList(inline=true, entry="time")
      @Namespace(prefix="l", reference="http://www.domain.com/list")
      private List<Integer> list = new Vector<Integer>();
      
      public FarmExample(@Element(name="chicken") Chicken chicken, @Element(name="cow") Cow cow) {
         this.farmer = new Farmer("Old McDonald", 80);
         this.chicken = chicken;
         this.cow = cow;
         
      }
      public List<Integer> getTime() {
         return list;
      }
      public Chicken getChicken() {
         return chicken;
      }
      public Cow getCow() {
         return cow;
      }
   }
   
   public void testAnnotationStrategy() throws Exception {
      Strategy strategy = new AnnotationStrategy();
      Serializer serializer = new Persister(strategy);
      StringWriter writer = new StringWriter();
      FarmExample example = serializer.read(FarmExample.class, SOURCE);
      
      example.getTime().add(10);
      example.getTime().add(11);
      example.getTime().add(12);
      
      assertEquals(example.getCow().getName(), "Bull");
      assertEquals(example.getCow().getAge(), 4);
      assertEquals(example.getCow().getLegs(), 4);
      assertEquals(example.getChicken().getName(), "Hen");
      assertEquals(example.getChicken().getAge(), 1);
      assertEquals(example.getChicken().getLegs(), 2);
      
      serializer.write(example, System.out);
      serializer.write(example, writer);
      
      String text = writer.toString();
      
      assertElementExists(text, "/farmExample/chicken");
      assertElementExists(text, "/farmExample/cow");
      assertElementHasNamespace(text, "/farmExample/chicken", "http://www.domain.com/test");
      assertElementDoesNotHaveNamespace(text, "/farmExample/cow", "http://www.domain.com/test");
      assertElementHasAttribute(text, "/farmExample/chicken", "name", "Hen");
      assertElementHasAttribute(text, "/farmExample/chicken", "age", "1");
      assertElementHasAttribute(text, "/farmExample/chicken", "legs", "2");
      assertElementHasAttribute(text, "/farmExample/cow", "name", "Bull");
      assertElementHasAttribute(text, "/farmExample/cow", "age", "4");
      assertElementHasAttribute(text, "/farmExample/cow", "legs", "4");
   }
}
