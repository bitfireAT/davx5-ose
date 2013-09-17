package org.simpleframework.xml.core;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;

@SuppressWarnings("all")
public class ConstructorInjectionExampleTest extends TestCase {
   
   private static final String SOURCE_A =
   "<showConstructorForEachUnionEntry>" +
   "  <a>text</a>"+
   "</showConstructorForEachUnionEntry>";
   
   private static final String SOURCE_B =
   "<showConstructorForEachUnionEntry>" +
   "  <b>1</b>"+
   "</showConstructorForEachUnionEntry>";
   
   private static final String SOURCE_C =
   "<showConstructorForEachUnionEntry>" +
   "  <c>12.9</c>"+
   "</showConstructorForEachUnionEntry>";
   
   private static final String SUBTYPE_B =
   "<showSubtypeInjection>" +
   "  <b/>"+
   "</showSubtypeInjection>";
   
   private static final String SUBTYPE_C =
   "<showSubtypeInjection>" +
   "  <c/>"+
   "</showSubtypeInjection>";
   
   private static final String AMBIGUOUS_ATT =
   "<showSubtypeInjection a='value'/>";

   private static final String AMBIGUOUS_EL =
   "<showAmbiguousNames>" +
   "  <a>text</a>"+
   "</showAmbiguousNames>";  
   
   private static final String AMBIGUOUS_ATT_EL =
   "<showAmbiguousNames a='blah'>" +
   "  <a>some text</a>"+
   "</showAmbiguousNames>";  
   
   private static final String PATH_INJECTION =
   "<showInjectionWithoutPath>"+
   "  <a>" +
   "    <b>"+
   "      <c>"+
   "         <x>some text x</x>" +
   "      </c>"+
   "    </b>"+
   "    <b>"+
   "      <y>some y</y>"+
   "    </b>"+
   "  </a>"+
   "</showInjectionWithoutPath>";
   
   private static final String PATH_INJECTION_UNION_INT =
   "<showInjectionWithoutPath>"+
   "  <a>" +
   "    <b>"+
   "      <c>"+
   "         <int>22</int>" +
   "      </c>"+
   "    </b>"+
   "    <b>"+
   "      <y>some y</y>"+
   "    </b>"+
   "  </a>"+
   "</showInjectionWithoutPath>";
   
   private static final String PATH_INJECTION_UNION_DOUBLE =
   "<showInjectionWithoutPath>"+
   "  <a>" +
   "    <b>"+
   "      <c>"+
   "         <double>32.178</double>" +
   "      </c>"+
   "    </b>"+
   "    <b>"+
   "      <y>some y</y>"+
   "    </b>"+
   "  </a>"+
   "</showInjectionWithoutPath>";
   
   @Root
   private static class ShowConstructorForEachUnionEntry{
      @ElementUnion({
         @Element(name="a", type=String.class),
         @Element(name="b", type=Integer.class),
         @Element(name="c", type=Double.class)
      })
      private Object value;
      public ShowConstructorForEachUnionEntry(@Element(name="a") String a){ value = a; }
      public ShowConstructorForEachUnionEntry(@Element(name="b") Integer b){ value = b; }
      public ShowConstructorForEachUnionEntry(@Element(name="c") Double c){ value = c; }
   }
   
   private static class ShowSubtypeInjection {
      @ElementUnion({
         @Element(name="b", type=B.class),
         @Element(name="c", type=C.class)
      })
      private A value;
      public ShowSubtypeInjection(@Element(name="b") B b){ value = b; }
      public ShowSubtypeInjection(@Element(name="c") C c){ value = c; }
   }
   
   private static class ShowAmbiguousNames {
      @Element(name="a", required=false)
      private String el;
      @Attribute(name="a", required=false)
      private String att;
      public ShowAmbiguousNames(@Element(name="a") String el) { this.el = el; }
      public ShowAmbiguousNames(@Element(name="a") String el, @Attribute(name="a") String att) { this.el = el; this.att = att; }
   }
   
   private static class ShowInjectionWithoutPath {
      @Path("a/b[1]/c")
      @Element(name="x")
      private final String x;
      @Path("a/b[2]")
      @Element(name="y")
      private final String y;
      public ShowInjectionWithoutPath(@Element(name="x") String x, @Element(name="y") String y) { this.x = x; this.y = y; }
   }
   
   private static class ShowInjectionWithoutPathUsingUnion {
      @Path("a/b[1]/c")
      @ElementUnion({
         @Element(name="int", type=Integer.class),
         @Element(name="double", type=Double.class),
         @Element(name="text", type=String.class)
      })
      private final Object x;
      @Path("a/b[2]")
      @Element(name="y")
      private final String y;
      public ShowInjectionWithoutPathUsingUnion(@Element(name="int") int x, @Element(name="y") String y) { this.x = x; this.y = y; }
      public ShowInjectionWithoutPathUsingUnion(@Element(name="double") double x, @Element(name="y") String y) { this.x = x; this.y = y; }
   }
   
   @Root
   private static class A {}
   private static class B extends A{}
   private static class C extends B{}
   
   public void testUnionConstruction() throws Exception {
      Persister persister = new Persister();
      ShowConstructorForEachUnionEntry a = persister.read(ShowConstructorForEachUnionEntry.class, SOURCE_A);
      ShowConstructorForEachUnionEntry b = persister.read(ShowConstructorForEachUnionEntry.class, SOURCE_B);
      ShowConstructorForEachUnionEntry c = persister.read(ShowConstructorForEachUnionEntry.class, SOURCE_C);
      assertEquals(a.value, "text");
      assertEquals(b.value, 1);
      assertEquals(c.value, 12.9);
   }
   
   public void testSubtypeInjection() throws Exception {
      Persister persister = new Persister();
      ShowSubtypeInjection b = persister.read(ShowSubtypeInjection.class, SUBTYPE_B);
      ShowSubtypeInjection c = persister.read(ShowSubtypeInjection.class, SUBTYPE_C);
      assertEquals(b.value.getClass(), B.class);
      assertEquals(c.value.getClass(), C.class);
   }
   
   public void showAmbiguousNames() throws Exception {
      Persister persister = new Persister();
      ShowAmbiguousNames a = persister.read(ShowAmbiguousNames.class, AMBIGUOUS_ATT);
      ShowAmbiguousNames b = persister.read(ShowAmbiguousNames.class, AMBIGUOUS_EL);
      ShowAmbiguousNames c = persister.read(ShowAmbiguousNames.class, AMBIGUOUS_ATT_EL);
      assertEquals(a.att, "value");
      assertEquals(a.el, null);
      assertEquals(a.att, null);
      assertEquals(a.el, "text");
      assertEquals(a.att, "blah");
      assertEquals(a.el, "some text");
   }
   
   public void testWithoutPath() throws Exception {
      Persister persister = new Persister();
      ShowInjectionWithoutPath a = persister.read(ShowInjectionWithoutPath.class, PATH_INJECTION);
      assertEquals(a.x, "some text x");
      assertEquals(a.y, "some y");
   }
   
   public void testWithoutPathUsingUnion() throws Exception {
      Persister persister = new Persister();
      ShowInjectionWithoutPathUsingUnion a = persister.read(ShowInjectionWithoutPathUsingUnion.class, PATH_INJECTION_UNION_INT);
      ShowInjectionWithoutPathUsingUnion b = persister.read(ShowInjectionWithoutPathUsingUnion.class, PATH_INJECTION_UNION_DOUBLE);
      assertEquals(a.x, 22);
      assertEquals(a.y, "some y");
      assertEquals(b.x, 32.178);
      assertEquals(b.y, "some y");
   }

}
