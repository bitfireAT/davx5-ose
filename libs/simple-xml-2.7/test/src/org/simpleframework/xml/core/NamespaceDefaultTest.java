package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class NamespaceDefaultTest extends ValidationTestCase {
   
   private static final String SOURCE =
   "<a xmlns:x='http://domain/x' xmlns='http://domain/z'>\n"+
   "    <y:b xmlns:y='http://domain/y'>\n"+
   "        <c xmlns='http://domain/c'>\n"+
   "            <d xmlns='http://domain/z'>d</d>\n"+
   "        </c>\n"+
   "    </y:b>\n"+
   "</a>\n";
   
   @Root
   @NamespaceList({
   @Namespace(prefix="x", reference="http://domain/x"),
   @Namespace(prefix="z", reference="http://domain/z")})
   @Namespace(reference="http://domain/z")
   private static class A {
      @Element
      @Namespace(prefix="y", reference="http://domain/y")
      private B b;
   }
   @Root
   private static class B {
      @Element
      @Namespace(reference="http://domain/c")
      private C c;
   }
   @Root
   private static class C{
      @Element
      @Namespace(reference="http://domain/z")
      private String d;
   }

   public void testScope() throws Exception {
      Persister persister = new Persister();
      StringWriter writer = new StringWriter();
      A example = persister.read(A.class, SOURCE);
      
      assertEquals(example.b.c.d, "d");
      
      assertElementHasNamespace(SOURCE, "/a", "http://domain/z");
      assertElementHasNamespace(SOURCE, "/a/b", "http://domain/y");
      assertElementHasNamespace(SOURCE, "/a/b/c", "http://domain/c");
      assertElementHasNamespace(SOURCE, "/a/b/c/d", "http://domain/z");
      
      persister.write(example, writer);
      
      String text = writer.toString();
      System.out.println(text);
      
      assertElementHasNamespace(text, "/a", "http://domain/z");
      assertElementHasNamespace(text, "/a/b", "http://domain/y");
      assertElementHasNamespace(text, "/a/b/c", "http://domain/c");
      assertElementHasNamespace(text, "/a/b/c/d", "http://domain/z");
      
      assertElementHasAttribute(text, "/a", "xmlns", "http://domain/z");
      assertElementDoesNotHaveAttribute(text, "/a", "xmlns:z", "http://domain/z");
      assertElementHasAttribute(text, "/a", "xmlns:x", "http://domain/x");
      assertElementHasAttribute(text, "/a/b", "xmlns:y", "http://domain/y");
      assertElementHasAttribute(text, "/a/b/c", "xmlns", "http://domain/c");
      assertElementHasAttribute(text, "/a/b/c/d", "xmlns", "http://domain/z");
   }
}
