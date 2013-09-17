package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.NamespaceList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class NamespaceVerbosityTest extends ValidationTestCase {

   private static final String SOURCE =
   "<a xmlns:x='http://domain/x' xmlns:y='http://domain/y'>\n"+
   "    <x:b>b</x:b>\n"+
   "    <y:c>c</y:c>\n"+
   "    <x:d>\n"+
   "        <e>e</e>\n"+
   "    </x:d>\n"+
   "</a>\n";
   
   @Root
   @NamespaceList({
   @Namespace(prefix="x", reference="http://domain/x"),
   @Namespace(prefix="y", reference="http://domain/y")})
   private static class A {
      @Element
      @Namespace(prefix="i", reference="http://domain/x") // ignore prefix as inherited
      private String b;
      @Element
      @Namespace(prefix="j", reference="http://domain/y") // ignore prefix as inherited
      private String c;
      @Element
      @Namespace(prefix="k", reference="http://domain/x") // ignore prefix as inherited
      private D d;
   }
   
   @Root
   private static class D {
      @Element
      private String e;
   }

   public void testScope() throws Exception {
      Persister persister = new Persister();
      StringWriter writer = new StringWriter();
      A example = persister.read(A.class, SOURCE);
      
      assertEquals(example.b, "b");
      assertEquals(example.c, "c");
      assertEquals(example.d.e, "e");
      
      assertElementHasNamespace(SOURCE, "/a", null);
      assertElementHasNamespace(SOURCE, "/a/b", "http://domain/x");
      assertElementHasNamespace(SOURCE, "/a/c", "http://domain/y");
      assertElementHasNamespace(SOURCE, "/a/d", "http://domain/x");
      assertElementHasNamespace(SOURCE, "/a/d/e", null);
      
      persister.write(example, writer);
      
      String text = writer.toString();
      System.out.println(text);
      
      assertElementHasNamespace(text, "/a", null);
      assertElementHasNamespace(text, "/a/b", "http://domain/x");
      assertElementHasNamespace(text, "/a/c", "http://domain/y");
      assertElementHasNamespace(text, "/a/d", "http://domain/x");
      assertElementHasNamespace(text, "/a/d/e", null);
      
      assertElementHasAttribute(text, "/a", "xmlns:x", "http://domain/x");
      assertElementHasAttribute(text, "/a", "xmlns:y", "http://domain/y");
      assertElementDoesNotHaveAttribute(text, "/a/b", "xmlns:i", "http://domain/x");
      assertElementDoesNotHaveAttribute(text, "/a/c", "xmlns:j", "http://domain/y");
      assertElementDoesNotHaveAttribute(text, "/a/d", "xmlns:k", "http://domain/x");
   }
}
