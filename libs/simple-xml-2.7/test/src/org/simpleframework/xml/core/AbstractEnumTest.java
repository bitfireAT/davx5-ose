package org.simpleframework.xml.core;

import java.io.StringWriter;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

public class AbstractEnumTest extends TestCase {

   public void testFoo() throws Exception {
   final Serializer serializer = new Persister();
   final FooObject fooObject = new FooObject();
   fooObject.setFoo(Foo.NAY);
   serializer.write(fooObject, new StringWriter());
   }

   @Root
   public static class FooObject {

   @Element
   private Foo foo;

   public FooObject() {
   }

   public Foo getFoo() {
   return foo;
   }

   public void setFoo(Foo foo) {
   this.foo = foo;
   }
   }

   public static enum Foo {
   YEA {
   public boolean foo() {
   return true;
   }
   },
   NAY {
   public boolean foo() {
   return false;
   }
   };

   public abstract boolean foo();
   }
}
