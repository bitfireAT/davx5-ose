package org.simpleframework.xml.core;

import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementListUnion;

public class UnionDependencyDoesNotMatchTypeTest extends ValidationTestCase {
   
   @Root
   private static class Example {
      @ElementListUnion({
         @ElementList(entry="x", inline=true, type=X.class),
         @ElementList(entry="y", inline=true, type=Y.class),
         @ElementList(entry="z", inline=true, type=Z.class),    
         @ElementList(entry="a", inline=true, type=A.class),
         @ElementList(entry="b", inline=true, type=B.class),
         @ElementList(entry="c", inline=true, type=C.class)         
      })
      private List<Entry> list = new ArrayList<Entry>();
      public void add(Entry e) {
         list.add(e);
      }
   }
   @Root
   private static abstract class Entry{}
   private static class X extends Entry{}
   private static class Y extends Entry{}
   private static class Z extends Entry{}
   @Root
   private static abstract class Node{}
   private static class A extends Node{}
   private static class B extends Node{}
   private static class C extends Node{}
   
   public void testTypeMatch() throws Exception {
      Persister persister = new Persister();
      Example example = new Example();
      example.add(new X());
      example.add(new Y());
      example.add(new Z());
      example.add(new Z());
      persister.write(example, System.out);
      validate(persister, example);
   }
}
