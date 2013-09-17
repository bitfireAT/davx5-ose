package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.Set;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.InstantiationException;
import org.simpleframework.xml.core.Persister;

import junit.framework.TestCase;
import org.simpleframework.xml.ValidationTestCase;

public class CollectionTest extends ValidationTestCase {
        
   private static final String LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<test name='example'>\n"+
   "   <list>\n"+   
   "      <entry id='1'>\n"+
   "         <text>one</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='2'>\n"+
   "         <text>two</text>  \n\r"+
   "      </entry>\n"+
   "      <entry id='3'>\n"+
   "         <text>three</text>  \n\r"+
   "      </entry>\n"+
   "   </list>\n"+
   "</test>";  

   private static final String ARRAY_LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<test name='example'>\n"+
   "   <list class='java.util.ArrayList'>\n"+   
   "      <entry id='1'>\n"+
   "         <text>one</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='2'>\n"+
   "         <text>two</text>  \n\r"+
   "      </entry>\n"+
   "      <entry id='3'>\n"+
   "         <text>three</text>  \n\r"+
   "      </entry>\n"+
   "   </list>\n"+
   "</test>";     
   
   private static final String HASH_SET = 
   "<?xml version=\"1.0\"?>\n"+
   "<test name='example'>\n"+
   "   <list class='java.util.HashSet'>\n"+   
   "      <entry id='1'>\n"+
   "         <text>one</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='2'>\n"+
   "         <text>two</text>  \n\r"+
   "      </entry>\n"+
   "      <entry id='3'>\n"+
   "         <text>three</text>  \n\r"+
   "      </entry>\n"+
   "   </list>\n"+
   "</test>";    

   private static final String TREE_SET = 
   "<?xml version=\"1.0\"?>\n"+
   "<test name='example'>\n"+
   "   <list class='java.util.TreeSet'>\n"+   
   "      <entry id='1'>\n"+
   "         <text>one</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='2'>\n"+
   "         <text>two</text>  \n\r"+
   "      </entry>\n"+
   "      <entry id='3'>\n"+
   "         <text>three</text>  \n\r"+
   "      </entry>\n"+
   "   </list>\n"+
   "</test>";       
  
   private static final String ABSTRACT_LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<test name='example'>\n"+
   "   <list class='org.simpleframework.xml.core.CollectionTest$AbstractList'>\n"+   
   "      <entry id='1'>\n"+
   "         <text>one</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='2'>\n"+
   "         <text>two</text>  \n\r"+
   "      </entry>\n"+
   "      <entry id='3'>\n"+
   "         <text>three</text>  \n\r"+
   "      </entry>\n"+
   "   </list>\n"+
   "</test>";  
  
   private static final String NOT_A_COLLECTION = 
   "<?xml version=\"1.0\"?>\n"+
   "<test name='example'>\n"+
   "   <list class='java.util.Hashtable'>\n"+   
   "      <entry id='1'>\n"+
   "         <text>one</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='2'>\n"+
   "         <text>two</text>  \n\r"+
   "      </entry>\n"+
   "      <entry id='3'>\n"+
   "         <text>three</text>  \n\r"+
   "      </entry>\n"+
   "   </list>\n"+
   "</test>";  
   
   private static final String MISSING_COLLECTION = 
   "<?xml version=\"1.0\"?>\n"+
   "<test name='example'>\n"+
   "   <list class='example.MyCollection'>\n"+   
   "      <entry id='1'>\n"+
   "         <text>one</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='2'>\n"+
   "         <text>two</text>  \n\r"+
   "      </entry>\n"+
   "      <entry id='3'>\n"+
   "         <text>three</text>  \n\r"+
   "      </entry>\n"+
   "   </list>\n"+
   "</test>";  

   private static final String EXTENDED_ENTRY_LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<test name='example'>\n"+
   "   <list>\n"+   
   "      <extended-entry id='1' class='org.simpleframework.xml.core.CollectionTest$ExtendedEntry'>\n"+
   "         <text>one</text>  \n\r"+
   "         <description>this is an extended entry</description>\n\r"+
   "      </extended-entry>\n\r"+
   "      <extended-entry id='2' class='org.simpleframework.xml.core.CollectionTest$ExtendedEntry'>\n"+
   "         <text>two</text>  \n\r"+
   "         <description>this is the second one</description>\n"+
   "      </extended-entry>\n"+
   "      <entry id='3'>\n"+
   "         <text>three</text>  \n\r"+
   "      </entry>\n"+
   "   </list>\n"+
   "</test>";     
   
   private static final String TYPE_FROM_FIELD_LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<typeFromFieldList name='example'>\n"+
   "   <list>\n"+   
   "      <entry id='1'>\n"+
   "         <text>one</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='2'>\n"+
   "         <text>two</text>  \n\r"+
   "      </entry>\n"+
   "      <entry id='3'>\n"+
   "         <text>three</text>  \n\r"+
   "      </entry>\n"+
   "   </list>\n"+
   "</typeFromFieldList>";  
   
   private static final String TYPE_FROM_METHOD_LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<typeFromMethodList name='example'>\n"+
   "   <list>\n"+   
   "      <entry id='1'>\n"+
   "         <text>one</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='2'>\n"+
   "         <text>two</text>  \n\r"+
   "      </entry>\n"+
   "      <entry id='3'>\n"+
   "         <text>three</text>  \n\r"+
   "      </entry>\n"+
   "   </list>\n"+
   "</typeFromMethodList>";
   
   private static final String PRIMITIVE_LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<primitiveCollection name='example'>\n"+
   "   <list>\n"+   
   "      <text>one</text>  \n\r"+
   "      <text>two</text>  \n\r"+
   "      <text>three</text>  \n\r"+
   "   </list>\n"+
   "</primitiveCollection>";
   
   
   private static final String COMPOSITE_LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<compositeCollection name='example'>\n"+
   "   <list>\n"+
   "      <entry id='1'>\n"+
   "         <text>one</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='2'>\n"+
   "         <text>two</text>  \n\r"+
   "      </entry>\n\r"+
   "      <entry id='3'>\n"+
   "         <text>three</text>  \n\r"+
   "      </entry>\n\r"+
   "   </list>\n"+
   "</compositeCollection>";
   
   
   private static final String PRIMITIVE_DEFAULT_LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<primitiveDefaultCollection name='example'>\n"+
   "   <list>\n"+   
   "      <string>one</string>  \n\r"+
   "      <string>two</string>  \n\r"+
   "      <string>three</string>  \n\r"+
   "   </list>\n"+
   "</primitiveDefaultCollection>";  
   
   @Root(name="entry")
   private static class Entry implements Comparable {

      @Attribute(name="id")           
      private int id;           
           
      @Element(name="text")
      private String text;        

      public int compareTo(Object entry) {
         return id - ((Entry)entry).id;              
      }   
   }

   @Root(name="extended-entry")
   private static class ExtendedEntry extends Entry {

      @Element(name="description")
      private String description;           
   }

   @Root(name="test")
   private static class EntrySet implements Iterable<Entry> {

      @ElementList(name="list", type=Entry.class)
      private Set<Entry> list;           

      @Attribute(name="name")
      private String name;

      public Iterator<Entry> iterator() {
         return list.iterator();
      }
   }
   
   @Root(name="test")
   private static class EntrySortedSet implements Iterable<Entry> {

      @ElementList(name="list", type=Entry.class)
      private SortedSet<Entry> list;           

      @Attribute(name="name")
      private String name;

      public Iterator<Entry> iterator() {
         return list.iterator();  
      }
   }

   @Root(name="test")
   private static class EntryList implements Iterable<Entry> {

      @ElementList(name="list", type=Entry.class)
      private List<Entry> list;           

      @Attribute(name="name")
      private String name;

      public Iterator<Entry> iterator() {
         return list.iterator();  
      }
   }

   @Root(name="test")
   public static class InvalidList {

      @ElementList(name="list", type=Entry.class)
      private String list;

      @Attribute(name="name")
      private String name;      
   } 

   @Root(name="test")
   public static class UnknownCollectionList implements Iterable<Entry> {

      @ElementList(name="list", type=Entry.class)
      private UnknownCollection<Entry> list;

      @Attribute(name="name")
      private String name;   

      public Iterator<Entry> iterator() {
         return list.iterator();  
      }
   }
   
   @Root
   private static class TypeFromFieldList implements Iterable<Entry> {
      
      @ElementList
      private List<Entry> list;           

      @Attribute
      private String name;

      public Iterator<Entry> iterator() {
         return list.iterator();  
      }
   }
   
   @Root
   private static class TypeFromMethodList implements Iterable<Entry> {      
      
      private List<Entry> list;           

      @Attribute
      private String name;
      
      @ElementList
      public List<Entry> getList() {
         return list;
      }
      
      @ElementList
      public void setList(List<Entry> list) {
         this.list = list;
      }

      public Iterator<Entry> iterator() {
         return list.iterator();  
      }
   }

   @Root
   private static class PrimitiveCollection implements Iterable<String> {

      @ElementList(name="list", type=String.class, entry="text")
      private List<String> list;           

      @Attribute(name="name")
      private String name;

      public Iterator<String> iterator() {
         return list.iterator();  
      }
   }
   
   @Root
   private static class CompositeCollection implements Iterable<Entry> {

      @ElementList(name="list", entry="text")
      private List<Entry> list;           

      @Attribute(name="name")
      private String name;

      public Iterator<Entry> iterator() {
         return list.iterator();  
      }
   }
   
   
   @Root
   private static class PrimitiveDefaultCollection implements Iterable<String> {

      @ElementList
      private List<String> list;           

      @Attribute
      private String name;

      public Iterator<String> iterator() {
         return list.iterator();  
      }
   }
   
   private abstract class AbstractList<T> extends ArrayList<T> {

      public AbstractList() {
         super();              
      }        
   }
   
   private abstract class UnknownCollection<T> implements Collection<T> {

      public UnknownCollection() {
         super();              
      }           
   }
   
	private Persister serializer;

	public void setUp() {
	   serializer = new Persister();
	}
	
   public void testSet() throws Exception {    
      EntrySet set = serializer.read(EntrySet.class, LIST);
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(Entry entry : set) {
         if(entry.id == 1 && entry.text.equals("one")) {              
            one++;
         }
         if(entry.id == 2 && entry.text.equals("two")) {              
            two++;
         }
         if(entry.id == 3 && entry.text.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);
   }
   
   public void testSortedSet() throws Exception {    
      EntrySortedSet set = serializer.read(EntrySortedSet.class, LIST);
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(Entry entry : set) {
         if(entry.id == 1 && entry.text.equals("one")) {              
            one++;
         }
         if(entry.id == 2 && entry.text.equals("two")) {              
            two++;
         }
         if(entry.id == 3 && entry.text.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);
      
      validate(set, serializer);
   }

   public void testList() throws Exception {    
      EntryList set = serializer.read(EntryList.class, LIST);
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(Entry entry : set) {
         if(entry.id == 1 && entry.text.equals("one")) {              
            one++;
         }
         if(entry.id == 2 && entry.text.equals("two")) {              
            two++;
         }
         if(entry.id == 3 && entry.text.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);
      
      validate(set, serializer);
   }

   public void testHashSet() throws Exception {    
      EntrySet set = serializer.read(EntrySet.class, HASH_SET);
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(Entry entry : set) {
         if(entry.id == 1 && entry.text.equals("one")) {              
            one++;
         }
         if(entry.id == 2 && entry.text.equals("two")) {              
            two++;
         }
         if(entry.id == 3 && entry.text.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);
      
      validate(set, serializer);
   }  

   public void testTreeSet() throws Exception {    
      EntrySortedSet set = serializer.read(EntrySortedSet.class, TREE_SET);
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(Entry entry : set) {
         if(entry.id == 1 && entry.text.equals("one")) {              
            one++;
         }
         if(entry.id == 2 && entry.text.equals("two")) {              
            two++;
         }
         if(entry.id == 3 && entry.text.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);
      
      validate(set, serializer);
   }  
   
   public void testArrayList() throws Exception {    
      EntryList list = serializer.read(EntryList.class, ARRAY_LIST);
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(Entry entry : list) {
         if(entry.id == 1 && entry.text.equals("one")) {              
            one++;
         }
         if(entry.id == 2 && entry.text.equals("two")) {              
            two++;
         }
         if(entry.id == 3 && entry.text.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);
      
      validate(list, serializer);
   } 

   public void testSortedSetToSet() throws Exception {    
      EntrySet set = serializer.read(EntrySet.class, TREE_SET);
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(Entry entry : set) {
         if(entry.id == 1 && entry.text.equals("one")) {              
            one++;
         }
         if(entry.id == 2 && entry.text.equals("two")) {              
            two++;
         }
         if(entry.id == 3 && entry.text.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);
   }  

   public void testExtendedEntry() throws Exception {    
      EntrySet set = serializer.read(EntrySet.class, EXTENDED_ENTRY_LIST);
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(Entry entry : set) {
         if(entry.id == 1 && entry.text.equals("one")) {              
            one++;
         }
         if(entry.id == 2 && entry.text.equals("two")) {              
            two++;
         }
         if(entry.id == 3 && entry.text.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);

      StringWriter out = new StringWriter();
      serializer.write(set, out);
      serializer.write(set, System.err);
      EntrySet other = serializer.read(EntrySet.class, out.toString());

      for(Entry entry : set) {
         if(entry.id == 1 && entry.text.equals("one")) {              
            one++;
         }
         if(entry.id == 2 && entry.text.equals("two")) {              
            two++;
         }
         if(entry.id == 3 && entry.text.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 2);
      assertEquals(two, 2);
      assertEquals(three, 2);

      serializer.write(other, System.err);      
   }
   
   public void testTypeFromFieldList() throws Exception {    
      TypeFromFieldList list = serializer.read(TypeFromFieldList.class, TYPE_FROM_FIELD_LIST);
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(Entry entry : list) {
         if(entry.id == 1 && entry.text.equals("one")) {              
            one++;
         }
         if(entry.id == 2 && entry.text.equals("two")) {              
            two++;
         }
         if(entry.id == 3 && entry.text.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);
      
      validate(list, serializer);
   }
   
   public void testTypeFromMethodList() throws Exception {    
      TypeFromMethodList list = serializer.read(TypeFromMethodList.class, TYPE_FROM_METHOD_LIST);
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(Entry entry : list) {
         if(entry.id == 1 && entry.text.equals("one")) {              
            one++;
         }
         if(entry.id == 2 && entry.text.equals("two")) {              
            two++;
         }
         if(entry.id == 3 && entry.text.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);
      
      validate(list, serializer);
   }
   
   public void testPrimitiveCollection() throws Exception {    
      PrimitiveCollection list = serializer.read(PrimitiveCollection.class, PRIMITIVE_LIST);
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(String entry : list) {
         if(entry.equals("one")) {              
            one++;
         }
         if(entry.equals("two")) {              
            two++;
         }
         if(entry.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);
      
      validate(list, serializer);
   }
   
   // XXX This test needs to inline the entry= attribute so that
   // XXX we can use it to name the inserted entries
   public void testCompositeCollection() throws Exception {    
      CompositeCollection list = serializer.read(CompositeCollection.class, COMPOSITE_LIST);
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(Entry entry : list) {
         if(entry.id == 1 && entry.text.equals("one")) {              
            one++;
         }
         if(entry.id == 2 && entry.text.equals("two")) {              
            two++;
         }
         if(entry.id == 3 && entry.text.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);
      
      validate(list, serializer);
   }
   
   public void testPrimitiveDefaultCollection() throws Exception {    
      PrimitiveDefaultCollection list = serializer.read(PrimitiveDefaultCollection.class, PRIMITIVE_DEFAULT_LIST);      
      int one = 0;
      int two = 0;
      int three = 0;
      
      for(String entry : list) {
         if(entry.equals("one")) {              
            one++;
         }
         if(entry.equals("two")) {              
            two++;
         }
         if(entry.equals("three")) {              
            three++;
         }
      }         
      assertEquals(one, 1);
      assertEquals(two, 1);
      assertEquals(three, 1);
      
      validate(list, serializer);
   }
   
   public void testSetToSortedSet() throws Exception {    
      boolean success = false;

      try {      
         EntrySortedSet set = serializer.read(EntrySortedSet.class, HASH_SET);
      } catch(InstantiationException e) {
         e.printStackTrace();
         success = true;              
      }         
      assertTrue(success);
   }  

   public void testListToSet() throws Exception {    
      boolean success = false;

      try {      
         EntrySet set = serializer.read(EntrySet.class, ARRAY_LIST);
      } catch(InstantiationException e) {
         e.printStackTrace();
         success = true;              
      }         
      assertTrue(success);
   }  


   public void testInvalidList() throws Exception {    
      boolean success = false;

      try {      
         InvalidList set = serializer.read(InvalidList.class, LIST);
      } catch(InstantiationException e) {
         e.printStackTrace();
         success = true;              
      }         
      assertTrue(success);
   }  

   public void testUnknownCollectionList() throws Exception {    
      boolean success = false;

      try {      
         UnknownCollectionList set = serializer.read(UnknownCollectionList.class, LIST);
      } catch(InstantiationException e) {
         e.printStackTrace();
         success = true;              
      }         
      assertTrue(success);
   }  

   public void testAbstractList() throws Exception {    
      boolean success = false;

      try {      
         EntryList set = serializer.read(EntryList.class, ABSTRACT_LIST);
      } catch(InstantiationException e) {
         e.printStackTrace();
         success = true;              
      }         
      assertTrue(success);
   }  
   
   public void testNotACollection() throws Exception {    
      boolean success = false;

      try {      
         EntryList set = serializer.read(EntryList.class, NOT_A_COLLECTION);
      } catch(InstantiationException e) {
         e.printStackTrace();
         success = true;              
      }         
      assertTrue(success);
   }  
   
   public void testMissingCollection() throws Exception {
      boolean success = false;

      try {      
         EntrySet set = serializer.read(EntrySet.class, MISSING_COLLECTION);
      } catch(ClassNotFoundException e) {
         e.printStackTrace();
         success = true;              
      }         
      assertTrue(success);           
   }
}
