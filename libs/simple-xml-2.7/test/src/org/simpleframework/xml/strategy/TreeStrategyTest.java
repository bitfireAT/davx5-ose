package org.simpleframework.xml.strategy;

import java.io.StringWriter;
import java.util.Collection;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.filter.Filter;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.strategy.TreeStrategy;

public class TreeStrategyTest extends ValidationTestCase {

   public static final int ITERATIONS = 10000; 

   public static final int MAXIMUM = 1000;   
        
   public static final String BASIC_ENTRY =
   "<?xml version=\"1.0\"?>\n"+
   "<root number='1234' flag='true'>\n"+
   "   <name>{example.name}</name>  \n\r"+
   "   <path>{example.path}</path>\n"+
   "   <constant>{no.override}</constant>\n"+
   "   <text>\n"+
   "        Some example text where {example.name} is replaced\n"+
   "        with the system property value and the path is\n"+
   "        replaced with the path {example.path}\n"+
   "   </text>\n"+
   "   <child name='first'>\n"+
   "      <one>this is the first element</one>\n"+
   "      <two>the second element</two>\n"+
   "      <three>the third elment</three>\n"+
   "      <grand-child>\n"+
   "         <entry-one key='name.1'>\n"+
   "            <value>value.1</value>\n"+
   "         </entry-one>\n"+
   "         <entry-two key='name.2'>\n"+
   "            <value>value.2</value>\n"+
   "         </entry-two>\n"+
   "      </grand-child>\n"+
   "   </child>\n"+
   "   <list TYPE='java.util.ArrayList'>\n"+
   "     <entry key='name.1'>\n"+
   "        <value>value.1</value>\n"+
   "     </entry>\n"+
   "     <entry key='name.2'>\n"+
   "        <value>value.2</value>\n"+
   "     </entry>\n"+
   "     <entry key='name.3'>\n"+
   "        <value>value.4</value>\n"+
   "     </entry>\n"+
   "     <entry key='name.4'>\n"+
   "        <value>value.4</value>\n"+
   "     </entry>\n"+
   "     <entry key='name.5'>\n"+
   "        <value>value.5</value>\n"+
   "     </entry>\n"+
   "  </list>\n"+ 
   "</root>";
   
   public static final String TEMPLATE_ENTRY =
   "<?xml version=\"1.0\"?>\n"+
   "<root number='1234' flag='true'>\n"+
   "   <name>${example.name}</name>  \n\r"+
   "   <path>${example.path}</path>\n"+
   "   <constant>${no.override}</constant>\n"+
   "   <text>\n"+
   "        Some example text where ${example.name} is replaced\n"+
   "        with the system property value and the path is \n"+
   "        replaced with the path ${example.path}\n"+
   "   </text>\n"+
   "   <child name='first'>\n"+
   "      <one>this is the first element</one>\n"+
   "      <two>the second element</two>\n"+
   "      <three>the third elment</three>\n"+
   "      <grand-child>\n"+
   "         <entry-one key='name.1'>\n"+
   "            <value>value.1</value>\n"+
   "         </entry-one>\n"+
   "         <entry-two key='name.2'>\n"+
   "            <value>value.2</value>\n"+
   "         </entry-two>\n"+
   "      </grand-child>\n"+
   "   </child>\n"+
   "   <list TYPE='java.util.ArrayList'>\n"+
   "     <entry key='name.1'>\n"+
   "        <value>value.1</value>\n"+
   "     </entry>\n"+
   "     <entry key='name.2'>\n"+
   "        <value>value.2</value>\n"+
   "     </entry>\n"+
   "     <entry key='name.3'>\n"+
   "        <value>value.4</value>\n"+
   "     </entry>\n"+
   "     <entry key='name.4'>\n"+
   "        <value>value.4</value>\n"+
   "     </entry>\n"+
   "     <entry key='name.5'>\n"+
   "        <value>value.5</value>\n"+
   "     </entry>\n"+
   "  </list>\n"+ 
   "</root>";

   @Root(name="root")
   public static class RootEntry {

      @Attribute(name="number")
      private int number;     

      @Attribute(name="flag")
      private boolean bool;
      
      @Element(name="constant")
      private String constant;

      @Element(name="name")
      private String name;

      @Element(name="path")
      private String path;

      @Element(name="text")
      private String text;

      @Element(name="child")
      private ChildEntry entry;

      @ElementList(name="list", type=ElementEntry.class)
      private Collection list;              
   }

   @Root(name="child")
   public static class ChildEntry {

      @Attribute(name="name")           
      private String name;   

      @Element(name="one")
      private String one;

      @Element(name="two")
      private String two;

      @Element(name="three")
      private String three;

      @Element(name="grand-child")
      private GrandChildEntry grandChild;
   }

   @Root(name="grand-child")
   public static class GrandChildEntry {

      @Element(name="entry-one")           
      private ElementEntry entryOne;

      @Element(name="entry-two")
      private ElementEntry entryTwo;
   }

   @Root(name="entry")
   public static class ElementEntry {

      @Attribute(name="key")
      private String name;

      @Element(name="value")
      private String value;                 
   }

   private static class EmptyFilter implements Filter {
           
      public String replace(String name) {
         return null;              
      }
   }

   static {
      System.setProperty("example.name", "some name");
      System.setProperty("example.path", "/some/path");
      System.setProperty("no.override", "some constant");
   } 

   private Persister systemSerializer;
   private Strategy strategy;

   public void setUp() throws Exception {
      strategy = new TreeStrategy("TYPE", "LENGTH");
      systemSerializer = new Persister(strategy);
   }
   
   public void testBasicDocument() throws Exception { 
      RootEntry entry = (RootEntry)systemSerializer.read(RootEntry.class, BASIC_ENTRY);

      long start = System.currentTimeMillis();

      for(int i = 0; i < ITERATIONS; i++) {
         systemSerializer.read(RootEntry.class, BASIC_ENTRY);
      }
      long duration = System.currentTimeMillis() - start;
      
      System.err.printf("Took '%s' ms to process %s documents\n", duration, ITERATIONS);
      systemSerializer.write(entry, System.out);
      
      StringWriter out = new StringWriter();
      systemSerializer.write(entry, out);
      validate(entry, systemSerializer);
      entry = (RootEntry)systemSerializer.read(RootEntry.class, out.toString());
      systemSerializer.write(entry, System.out);
   }

   public void testTemplateDocument() throws Exception {    
      RootEntry entry = (RootEntry)systemSerializer.read(RootEntry.class, TEMPLATE_ENTRY);

      long start = System.currentTimeMillis();

      for(int i = 0; i < ITERATIONS; i++) {
         systemSerializer.read(RootEntry.class, TEMPLATE_ENTRY);
      }
      long duration = System.currentTimeMillis() - start;
      
      System.err.printf("Took '%s' ms to process %s documents with templates\n", duration, ITERATIONS);
      systemSerializer.write(entry, System.out);

      StringWriter out = new StringWriter();
      systemSerializer.write(entry, out);
      validate(entry, systemSerializer);
      entry = (RootEntry)systemSerializer.read(RootEntry.class, out.toString());
      systemSerializer.write(entry, System.out);
   }

   public void testEmptyFilter() throws Exception {    
      systemSerializer = new Persister(strategy, new EmptyFilter());
      RootEntry entry = (RootEntry)systemSerializer.read(RootEntry.class, TEMPLATE_ENTRY);

      long start = System.currentTimeMillis();

      for(int i = 0; i < ITERATIONS; i++) {
         systemSerializer.read(RootEntry.class, TEMPLATE_ENTRY);
      }
      long duration = System.currentTimeMillis() - start;
      
      System.err.printf("Took '%s' ms to process %s documents with an empty filter\n", duration, ITERATIONS);
      systemSerializer.write(entry, System.out);

      StringWriter out = new StringWriter();
      systemSerializer.write(entry, out);
      validate(entry, systemSerializer);
      entry = (RootEntry)systemSerializer.read(RootEntry.class, out.toString());
      systemSerializer.write(entry, System.out);
   }

   public void testBasicWrite() throws Exception {
      RootEntry entry = (RootEntry)systemSerializer.read(RootEntry.class, BASIC_ENTRY);
      long start = System.currentTimeMillis();

      entry.constant = ">><<"; // this should be escaped
      entry.text = "this is text>> some more<<"; // this should be escaped
      
      for(int i = 0; i < ITERATIONS; i++) {              
         systemSerializer.write(entry, new StringWriter());
      }
      long duration = System.currentTimeMillis() - start;
      
      System.err.printf("Took '%s' ms to write %s documents\n", duration, ITERATIONS);
      systemSerializer.write(entry, System.out);

      StringWriter out = new StringWriter();
      systemSerializer.write(entry, out);
      validate(entry, systemSerializer);
      entry = (RootEntry)systemSerializer.read(RootEntry.class, out.toString());
      systemSerializer.write(entry, System.out);
   }
}
