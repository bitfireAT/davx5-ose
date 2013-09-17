package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class NamespaceScopeTest extends ValidationTestCase {
   
   private static final String SOURCE =
   "<a xmlns='http://domain/a'>\n"+
   "   <pre:b xmlns:pre='http://domain/b'>\n"+
   "      <c>c</c>\n"+
   "      <d xmlns=''>\n"+
   "          <e>e</e>\n"+
   "      </d>\n"+
   "   </pre:b>\n"+
   "</a>";
   
   @Root
   @Namespace(reference="http://domain/a")
   private static class A {
      @Element
      @Namespace(prefix="pre", reference="http://domain/b")
      private B b;
   }
   @Root
   private static class B {
      @Element
      private String c;
      @Element
      @Namespace
      private D d;
   }
   @Root
   private static class D{
      @Element
      private String e;
   }
   
   public void testScope() throws Exception {
      Persister persister = new Persister();
      StringWriter writer = new StringWriter();
      A example = persister.read(A.class, SOURCE);
      
      assertEquals(example.b.c, "c");
      assertEquals(example.b.d.e, "e");
      
      assertElementHasNamespace(SOURCE, "/a", "http://domain/a");
      assertElementHasNamespace(SOURCE, "/a/b", "http://domain/b");
      assertElementHasNamespace(SOURCE, "/a/b/c", "http://domain/a");
      assertElementHasNamespace(SOURCE, "/a/b/d", null);
      assertElementHasNamespace(SOURCE, "/a/b/d/e", null);
      
      persister.write(example, writer);
      
      String text = writer.toString();
      System.out.println(text);
      
      assertElementHasNamespace(text, "/a", "http://domain/a");
      assertElementHasNamespace(text, "/a/b", "http://domain/b");
      assertElementHasNamespace(text, "/a/b/c", "http://domain/a");
      assertElementHasNamespace(text, "/a/b/d", null);
      assertElementHasNamespace(text, "/a/b/d/e", null);
   }

}
