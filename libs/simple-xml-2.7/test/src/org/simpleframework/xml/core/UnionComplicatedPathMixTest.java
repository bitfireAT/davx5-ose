package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.ElementMapUnion;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.stream.CamelCaseStyle;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.Style;

public class UnionComplicatedPathMixTest extends ValidationTestCase {

   @Root
   @NamespaceList({
      @Namespace(prefix="x", reference="http://www.x.com/x")
   })
   private static class ComplicatedExample {
      @Path("x:path[1]")
      @ElementMapUnion({
         @ElementMap(entry="a", keyType=X.class, valueType=A.class, inline=true),
         @ElementMap(entry="b", keyType=Y.class, valueType=B.class, inline=true),
         @ElementMap(entry="c", keyType=Z.class, valueType=C.class, inline=true)
      })
      private final Map<Key, Entry> map;
      @Path("x:path[2]")
      @ElementListUnion({
         @ElementList(entry="a", type=A.class, inline=true),
         @ElementList(entry="b", type=B.class, inline=true),
         @ElementList(entry="c", type=C.class, inline=true)
      })
      private final List<Entry> list;
      @Path("x:path[2]")
      @Element
      private final String elementOne;
      @Path("x:path[2]")
      @Element
      private final String elementTwo;
      @Path("x:path[2]")
      @Element
      private final String elementThree;
      @Path("x:path[2]/x:someOtherPath")
      @Text
      private final String text;
      @Path("x:path[2]/x:someOtherPath")
      @Attribute
      private final String attribute_one;
      @Path("x:path[2]/x:someOtherPath")
      @Attribute
      private final String attribute_two;
      public ComplicatedExample(
            @Path("x:path[1]") @ElementMap(name="a") Map<Key, Entry> map,
            @Path("x:path[2]") @ElementList(name="a") List<Entry> list,
            @Element(name="elementOne") String elementOne,
            @Element(name="elementTwo") String elementTwo,
            @Element(name="elementThree") String elementThree,
            @Text String text,
            @Path("x:path[2]/x:someOtherPath") @Attribute(name="attribute_one") String attribute_one,
            @Path("x:path[2]/x:someOtherPath") @Attribute(name="attribute_two") String attribute_two) 
      {
         this.map = map;
         this.list = list;
         this.elementOne = elementOne;
         this.elementTwo = elementTwo;
         this.elementThree = elementThree;
         this.text = text;
         this.attribute_one = attribute_one;
         this.attribute_two = attribute_two;
      }
   }
   
   @Root
   private static class Key {}
   private static class X extends Key {}
   private static class Y extends Key {}
   private static class Z extends Key {}
   
   @Root
   private static class Entry {
      @Attribute
      private final String id;
      protected Entry(String id) {
         this.id = id;
      }
   }
   private static class A extends Entry {
      public A(@Attribute(name="id") String id) {
         super(id);
      }
   }
   private static class B extends Entry {
      public B(@Attribute(name="id") String id) {
         super(id);
      }
   }
   private static class C extends Entry {
      public C(@Attribute(name="id") String id) {
         super(id);
      }
   }
   
   public void testComplicatedMap() throws Exception {
      Map<Key, Entry> map = new LinkedHashMap<Key, Entry>();
      List<Entry> list = new ArrayList<Entry>();
      ComplicatedExample example = new ComplicatedExample(
            map, 
            list, 
            "element 1", 
            "element 2", 
            "element 3", 
            "text", 
            "attribute 1", 
            "attribute 2");
      map.put(new X(), new A("1"));
      map.put(new Z(), new C("2"));
      map.put(new Z(), new C("3"));
      map.put(new Y(), new B("4"));
      list.add(new C("a"));
      list.add(new A("b"));
      list.add(new B("c"));
      list.add(new B("d"));
      Persister persister = new Persister();
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      String resultingXml = writer.toString();
      System.out.println(resultingXml);
      assertElementExists(resultingXml, "complicatedExample/path");
      assertElementHasNamespace(resultingXml, "complicatedExample/path", "http://www.x.com/x");
      assertElementExists(resultingXml, "complicatedExample/path[1]/a");
      assertElementExists(resultingXml, "complicatedExample/path[1]/a[1]/x");
      assertElementExists(resultingXml, "complicatedExample/path[1]/a[1]/a");
      assertElementHasAttribute(resultingXml, "complicatedExample/path[1]/a[1]/a", "id", "1");
      assertElementExists(resultingXml, "complicatedExample/path[1]/c");
      assertElementExists(resultingXml, "complicatedExample/path[1]/c[1]/z");
      assertElementExists(resultingXml, "complicatedExample/path[1]/c[1]/c");
      assertElementHasAttribute(resultingXml, "complicatedExample/path[1]/c[1]/c", "id", "2");
      assertElementExists(resultingXml, "complicatedExample/path[1]/c");
      assertElementExists(resultingXml, "complicatedExample/path[1]/c[2]/z");
      assertElementExists(resultingXml, "complicatedExample/path[1]/c[2]/c");
      assertElementHasAttribute(resultingXml, "complicatedExample/path[1]/c[2]/c", "id", "3");
      assertElementExists(resultingXml, "complicatedExample/path[1]/b");
      assertElementExists(resultingXml, "complicatedExample/path[1]/b[1]/y");
      assertElementExists(resultingXml, "complicatedExample/path[1]/b[1]/b");
      assertElementHasAttribute(resultingXml, "complicatedExample/path[1]/b[1]/b", "id", "4");
      assertElementExists(resultingXml, "complicatedExample/path[2]/c");
      assertElementHasAttribute(resultingXml, "complicatedExample/path[2]/c", "id", "a");
      assertElementExists(resultingXml, "complicatedExample/path[2]/a");
      assertElementHasAttribute(resultingXml, "complicatedExample/path[2]/a", "id", "b");
      assertElementExists(resultingXml, "complicatedExample/path[2]/b");
      assertElementHasAttribute(resultingXml, "complicatedExample/path[2]/b", "id", "c");
      assertElementExists(resultingXml, "complicatedExample/path[2]/b");
      assertElementHasValue(resultingXml, "complicatedExample/path[2]/elementOne", "element 1");
      assertElementHasValue(resultingXml, "complicatedExample/path[2]/elementTwo", "element 2");
      assertElementHasValue(resultingXml, "complicatedExample/path[2]/elementThree", "element 3");
      assertElementHasValue(resultingXml, "complicatedExample/path[2]/someOtherPath", "text");
      assertElementHasNamespace(resultingXml, "complicatedExample/path[2]/someOtherPath", "http://www.x.com/x");
      assertElementHasAttribute(resultingXml, "complicatedExample/path[2]/someOtherPath", "attribute_one", "attribute 1");
      assertElementHasAttribute(resultingXml, "complicatedExample/path[2]/someOtherPath", "attribute_two", "attribute 2");
      validate(persister, example);
   }
   
   public void testStyledComplicatedMap() throws Exception {
      Style style = new CamelCaseStyle();
      Format format = new Format(style);
      Map<Key, Entry> map = new LinkedHashMap<Key, Entry>();
      List<Entry> list = new ArrayList<Entry>();
      ComplicatedExample example = new ComplicatedExample(
            map, 
            list, 
            "element 1", 
            "element 2", 
            "element 3", 
            "text", 
            "attribute 1", 
            "attribute 2");
      map.put(new X(), new A("1"));
      map.put(new Z(), new C("2"));
      map.put(new Z(), new C("3"));
      map.put(new Y(), new B("4"));
      list.add(new C("a"));
      list.add(new A("b"));
      list.add(new B("c"));
      list.add(new B("d"));
      Persister persister = new Persister(format);
      StringWriter writer = new StringWriter();
      persister.write(example, writer);
      String resultingXml = writer.toString();
      System.out.println(resultingXml);
      assertElementExists(resultingXml, "ComplicatedExample/Path");
      assertElementHasNamespace(resultingXml, "ComplicatedExample/Path", "http://www.x.com/x");
      assertElementExists(resultingXml, "ComplicatedExample/Path[1]/A");
      assertElementExists(resultingXml, "ComplicatedExample/Path[1]/A[1]/X");
      assertElementExists(resultingXml, "ComplicatedExample/Path[1]/A[1]/A");
      assertElementHasAttribute(resultingXml, "ComplicatedExample/Path[1]/A[1]/A", "id", "1");
      assertElementExists(resultingXml, "ComplicatedExample/Path[1]/C");
      assertElementExists(resultingXml, "ComplicatedExample/Path[1]/C[1]/Z");
      assertElementExists(resultingXml, "ComplicatedExample/Path[1]/C[1]/C");
      assertElementHasAttribute(resultingXml, "ComplicatedExample/Path[1]/C[1]/C", "id", "2");
      assertElementExists(resultingXml, "ComplicatedExample/Path[1]/C");
      assertElementExists(resultingXml, "ComplicatedExample/Path[1]/C[2]/Z");
      assertElementExists(resultingXml, "ComplicatedExample/Path[1]/C[2]/Z");
      assertElementHasAttribute(resultingXml, "ComplicatedExample/Path[1]/C[2]/C", "id", "3");
      assertElementExists(resultingXml, "ComplicatedExample/Path[1]/B");
      assertElementExists(resultingXml, "ComplicatedExample/Path[1]/B[1]/Y");
      assertElementExists(resultingXml, "ComplicatedExample/Path[1]/B[1]/B");
      assertElementHasAttribute(resultingXml, "ComplicatedExample/Path[1]/B[1]/B", "id", "4");
      assertElementExists(resultingXml, "ComplicatedExample/Path[2]/C");
      assertElementHasAttribute(resultingXml, "ComplicatedExample/Path[2]/C", "id", "a");
      assertElementExists(resultingXml, "ComplicatedExample/Path[2]/A");
      assertElementHasAttribute(resultingXml, "ComplicatedExample/Path[2]/A", "id", "b");
      assertElementExists(resultingXml, "ComplicatedExample/Path[2]/A");
      assertElementHasAttribute(resultingXml, "ComplicatedExample/Path[2]/B", "id", "c");
      assertElementExists(resultingXml, "ComplicatedExample/Path[2]/B");
      assertElementHasValue(resultingXml, "ComplicatedExample/Path[2]/ElementOne", "element 1");
      assertElementHasValue(resultingXml, "ComplicatedExample/Path[2]/ElementTwo", "element 2");
      assertElementHasValue(resultingXml, "ComplicatedExample/Path[2]/ElementThree", "element 3");
      assertElementHasValue(resultingXml, "ComplicatedExample/Path[2]/SomeOtherPath", "text");
      assertElementHasNamespace(resultingXml, "ComplicatedExample/Path[2]/SomeOtherPath", "http://www.x.com/x");
      assertElementHasAttribute(resultingXml, "ComplicatedExample/Path[2]/SomeOtherPath", "attributeOne", "attribute 1");
      assertElementHasAttribute(resultingXml, "ComplicatedExample/Path[2]/SomeOtherPath", "attributeTwo", "attribute 2");
      validate(persister, example);
   }
}
