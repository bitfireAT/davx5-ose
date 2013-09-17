package org.simpleframework.xml.core;

import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.convert.Convert;
import org.simpleframework.xml.strategy.CycleStrategy;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.stream.CamelCaseStyle;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;
import org.simpleframework.xml.stream.Style;

public class ConverterDecorationTest extends ValidationTestCase {
   
   @Default  
   private static class ConverterDecoration {      
      private List<ConverterDecorationExample> list;
      private List<NormalExample> normal;
      public ConverterDecoration(@ElementList(name="list") List<ConverterDecorationExample> list, @ElementList(name="normal") List<NormalExample> normal) {
         this.list = list;
         this.normal = normal;
      }
   }
   
   @Root
   @Namespace(reference="http://blah/host1")
   @Convert(ConverterDecorationConverter.class)
   private static class ConverterDecorationExample {
      private final String name;
      public ConverterDecorationExample(String name){
         this.name = name;
      }
   }
   
   @Default
   @Namespace(reference="http://blah/normal")
   private static class NormalExample {
      private final String name;
      public NormalExample(@Element(name="name") String name){
         this.name = name;
      }
   }
   
   private static class ConverterDecorationConverter implements org.simpleframework.xml.convert.Converter<ConverterDecorationExample>{
      public ConverterDecorationExample read(InputNode node) throws Exception {
         String name = node.getValue();
         return new ConverterDecorationExample(name);
      }      
      public void write(OutputNode node, ConverterDecorationExample value) throws Exception {
         node.setValue(value.name);
      }
      
   }
   
   public void testConverter() throws Exception {
      Style style = new CamelCaseStyle();
      Format format = new Format(style);
      Strategy cycle = new CycleStrategy();
      Strategy strategy = new AnnotationStrategy(cycle);
      Persister persister = new Persister(strategy, format);
      List<ConverterDecorationExample> list = new ArrayList<ConverterDecorationExample>();
      List<NormalExample> normal = new ArrayList<NormalExample>();
      ConverterDecoration example = new ConverterDecoration(list, normal);
      ConverterDecorationExample duplicate = new ConverterDecorationExample("duplicate");
      NormalExample normalDuplicate = new NormalExample("duplicate");
      list.add(duplicate);
      list.add(new ConverterDecorationExample("a"));
      list.add(new ConverterDecorationExample("b"));
      list.add(new ConverterDecorationExample("c"));
      list.add(duplicate);
      list.add(new ConverterDecorationExample("d"));
      list.add(duplicate);
      normal.add(normalDuplicate);
      normal.add(new NormalExample("1"));
      normal.add(new NormalExample("2"));
      normal.add(normalDuplicate);
      persister.write(example, System.err);     
   }

}
