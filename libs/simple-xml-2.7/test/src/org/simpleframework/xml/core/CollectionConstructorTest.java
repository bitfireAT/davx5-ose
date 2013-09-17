package org.simpleframework.xml.core;

import java.util.Map;
import java.util.Vector;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;

public class CollectionConstructorTest extends TestCase {
   
   private static final String LIST =
   "<example>"+
   "  <entry name='a' value='1'/>"+
   "  <entry name='b' value='2'/>"+
   "</example>";
   
   private static final String MAP =
   "<example>"+
   "  <element key='A'>"+
   "     <entry name='a' value='1'/>"+
   "  </element>"+
   "  <element key='B'>"+
   "     <entry name='b' value='2'/>"+
   "  </element>"+
   "</example>";
   
   
   private static final String COMPOSITE =
   "<composite>"+
   "  <example class='org.simpleframework.xml.core.CollectionConstructorTest$ExtendedCollectionConstructor'>"+
   "    <entry name='a' value='1'/>"+
   "    <entry name='b' value='2'/>"+
   "  </example>"+
   "</composite>";
   
   @Root(name="example")
   private static class MapConstructor {
    
      @ElementMap(name="list", entry="element", key="key", attribute=true, inline=true)
      private final Map<String, Entry> map;
      
      public MapConstructor(@ElementMap(name="list", entry="element", key="key", attribute=true, inline=true) Map<String, Entry> map) {
         this.map = map;
      }
      
      public int size() {
         return map.size();
      }
   }
   
   @Root(name="example")
   private static class CollectionConstructor {
      
      @ElementList(name="list", inline=true)
      private final Vector<Entry> vector;
      
      public CollectionConstructor(@ElementList(name="list", inline=true) Vector<Entry> vector) {
         this.vector = vector;
      }
      
      public int size() {
         return vector.size();
      }
   }
   
   @Root(name="entry")
   private static class Entry {
   
      @Attribute(name="name")
      private final String name;
      
      @Attribute(name="value")
      private final String value;
      
      public Entry(@Attribute(name="name") String name, @Attribute(name="value") String value) {
         this.name = name;
         this.value = value;
      }
      
      public String getName() {
         return name;
      }
      
      public String getValue() {
         return value;
      }
   }
   
   @Root(name="example")
   private static class ExtendedCollectionConstructor extends CollectionConstructor {
      
      public ExtendedCollectionConstructor(@ElementList(name="list", inline=true) Vector<Entry> vector) {
         super(vector);
      }
   }
   
   @Root(name="composite")
   private static class CollectionConstructorComposite {
      
      @Element(name="example")
      private CollectionConstructor collection;
   
      public CollectionConstructor getCollection() {
         return collection;
      }
   }
   
   public void testCollectionConstructor() throws Exception {
      Persister persister = new Persister();
      CollectionConstructor constructor = persister.read(CollectionConstructor.class, LIST);
      
      assertEquals(constructor.size(), 2);
   }
   
   public void testMapConstructor() throws Exception {
      Persister persister = new Persister();
      MapConstructor constructor = persister.read(MapConstructor.class, MAP);
      
      assertEquals(constructor.size(), 2);
   }

   public void testCollectionConstructorComposite() throws Exception {
      Persister persister = new Persister();
      CollectionConstructorComposite composite = persister.read(CollectionConstructorComposite.class, COMPOSITE);
      
      assertEquals(composite.getCollection().getClass(), ExtendedCollectionConstructor.class);
      assertEquals(composite.getCollection().size(), 2);
   }
}
