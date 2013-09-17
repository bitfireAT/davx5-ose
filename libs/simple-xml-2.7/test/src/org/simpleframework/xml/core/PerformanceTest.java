package org.simpleframework.xml.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.Collection;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.filter.Filter;
import org.simpleframework.xml.strategy.Type;
import org.simpleframework.xml.strategy.Visitor;
import org.simpleframework.xml.strategy.VisitorStrategy;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeMap;
import org.simpleframework.xml.stream.OutputNode;
//import com.thoughtworks.xstream.XStream;

// This test will not work with XPP3 because its fucked!! KXML is ok!
public class PerformanceTest extends ValidationTestCase {

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
   "   <list class='java.util.ArrayList'>\n"+
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
   "   <list class='java.util.ArrayList'>\n"+
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
   public static class RootEntry implements Serializable {

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
   public static class ChildEntry implements Serializable {

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
   public static class GrandChildEntry implements Serializable {

      @Element(name="entry-one")           
      private ElementEntry entryOne;

      @Element(name="entry-two")
      private ElementEntry entryTwo;
   }

   @Root(name="entry")
   public static class ElementEntry implements Serializable {

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

   public void setUp() throws Exception {
      systemSerializer = new Persister();
   }
   
   public void testCompareToOtherSerializers() throws Exception { 
      Serializer simpleSerializer = new Persister(new VisitorStrategy(new Visitor(){
         public void read(Type type, NodeMap<InputNode> node) throws Exception {
            if(node.getNode().isRoot()) {
               System.err.println(node.getNode().getSource().getClass());
            }
         }
         public void write(Type type, NodeMap<OutputNode> node){}                
      }));
      RootEntry entry = simpleSerializer.read(RootEntry.class, BASIC_ENTRY);
      ByteArrayOutputStream simpleBuffer = new ByteArrayOutputStream();
      ByteArrayOutputStream javaBuffer = new ByteArrayOutputStream();
      //ByteArrayOutputStream xstreamBuffer = new ByteArrayOutputStream();
      ObjectOutputStream javaSerializer = new ObjectOutputStream(javaBuffer);
      //XStream xstreamSerializer = new XStream();
      
      simpleSerializer.write(entry, simpleBuffer);
      //xstreamSerializer.toXML(entry, xstreamBuffer);
      javaSerializer.writeObject(entry);

      byte[] simpleByteArray = simpleBuffer.toByteArray();
      //byte[] xstreamByteArray = xstreamBuffer.toByteArray();
      byte[] javaByteArray = javaBuffer.toByteArray();
      
      System.err.println("SIMPLE TOOK "+timeToSerializeWithSimple(RootEntry.class, simpleByteArray, ITERATIONS)+"ms");
      System.err.println("JAVA TOOK "+timeToSerializeWithJava(RootEntry.class, javaByteArray, ITERATIONS)+"ms");
      //System.err.println("XSTREAM TOOK "+timeToSerializeWithXStream(RootEntry.class, xstreamByteArray, ITERATIONS)+"ms");
      
      //System.err.println("XSTREAM --->>"+xstreamBuffer.toString());
      System.err.println("SIMPLE --->>"+simpleBuffer.toString());
   }
   
   private long timeToSerializeWithSimple(Class type, byte[] buffer, int count) throws Exception {
      Persister persister = new Persister();
      persister.read(RootEntry.class, new ByteArrayInputStream(buffer));
      long now = System.currentTimeMillis();
      
      for(int i = 0; i < count; i++) {
         persister.read(RootEntry.class, new ByteArrayInputStream(buffer));
      }
      return System.currentTimeMillis() - now;
   }
   
   private long timeToSerializeWithJava(Class type, byte[] buffer, int count) throws Exception {
      ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(buffer));
      stream.readObject();
      long now = System.currentTimeMillis();
      
      for(int i = 0; i < count; i++) {
         new ObjectInputStream(new ByteArrayInputStream(buffer)).readObject();
      }
      return System.currentTimeMillis() - now;
   }
   
   /*
   private long timeToSerializeWithXStream(Class type, byte[] buffer, int count) throws Exception {
      XStream stream = new XStream();
      stream.fromXML(new ByteArrayInputStream(buffer));
      long now = System.currentTimeMillis();
      
      for(int i = 0; i < count; i++) {
         stream.fromXML(new ByteArrayInputStream(buffer));
      }
      return System.currentTimeMillis() - now;  
   }*/
      
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
      systemSerializer = new Persister(new EmptyFilter());
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
