package org.simpleframework.xml.stream;

import java.io.StringWriter;

import org.simpleframework.xml.ValidationTestCase;

public class NodeWriterTest extends ValidationTestCase {

   public void testEarlyCommit() throws Exception {
      StringWriter out = new StringWriter();           
      OutputNode root = NodeBuilder.write(out);
      boolean failure = false;
      
      try {
         root.commit();
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Early commit should fail", failure);
   }
   public void testBasicWrite() throws Exception {
      StringWriter out = new StringWriter();           
      OutputNode root = NodeBuilder.write(out).getChild("root");

      assertTrue(root.isRoot());
      assertEquals("root", root.getName());
      
      root.setAttribute("id", "test");
      root.setAttribute("name", "testRoot");

      assertEquals("test", root.getAttributes().get("id").getValue());
      assertEquals("testRoot", root.getAttributes().get("name").getValue());
      
      OutputNode childOfRoot = root.getChild("child-of-root");
      childOfRoot.setAttribute("name", "child of root");

      assertFalse(childOfRoot.isRoot());
      assertEquals("child-of-root", childOfRoot.getName());
      assertEquals("child of root", childOfRoot.getAttributes().get("name").getValue());
      
      OutputNode childOfChildOfRoot = childOfRoot.getChild("child-of-child-of-root");
      childOfChildOfRoot.setValue("I am a child of the child-of-root element and a grand child of the root element");

      assertFalse(childOfChildOfRoot.isRoot());
      assertEquals("child-of-child-of-root", childOfChildOfRoot.getName());
      
      OutputNode anotherChildOfRoot = root.getChild("another-child-of-root");
      anotherChildOfRoot.setAttribute("id", "yet another child of root");
      anotherChildOfRoot.setValue("I am a sibling to child-of-root");

      assertFalse(anotherChildOfRoot.isRoot());
      assertEquals("another-child-of-root", anotherChildOfRoot.getName());
      
      OutputNode finalChildOfRoot = root.getChild("final-child-of-root");
      finalChildOfRoot.setValue("this element is a child of root");

      assertFalse(finalChildOfRoot.isRoot());
      assertTrue(root.isRoot());

      root.commit();
      validate(out.toString());
   }

   public void testWrite() throws Exception {
      StringWriter out = new StringWriter();           
      OutputNode root = NodeBuilder.write(out).getChild("root");      
      root.setAttribute("version", "1.0");

      assertTrue(root.isRoot());
      assertEquals("root", root.getName());

      OutputNode firstChild = root.getChild("first-child");
      firstChild.setAttribute("key", "1");
      firstChild.setValue("some value for first child");

      assertFalse(firstChild.isRoot());
      assertEquals("1", firstChild.getAttributes().get("key").getValue());

      OutputNode secondChild = root.getChild("second-child");
      secondChild.setAttribute("key", "2");
      secondChild.setValue("some value for second child");

      assertTrue(root.isRoot());
      assertFalse(secondChild.isRoot());
      assertEquals(firstChild.getChild("test"), null);

      OutputNode test = secondChild.getChild("test");
      test.setValue("test value");
      
      assertEquals(test.getName(), "test");
      assertEquals(test.getValue(), "test value");

      secondChild.commit();

      assertEquals(secondChild.getChild("example"), null);
      
      OutputNode thirdChild = root.getChild("third-child");
      thirdChild.setAttribute("key", "3");
      thirdChild.setAttribute("name", "three");

      assertEquals(thirdChild.getAttributes().get("key").getValue(), "3");      
      assertEquals(thirdChild.getAttributes().get("name").getValue(), "three");

      thirdChild.commit();

      assertEquals(thirdChild.getChild("foo"), null);
      assertEquals(thirdChild.getChild("bar"), null);

      OutputNode fourthChild = root.getChild("fourth-child");
      fourthChild.setAttribute("key", "4");
      fourthChild.setAttribute("name", "four");

      OutputNode fourthChildChild = fourthChild.getChild("fourth-child-child");
      fourthChildChild.setAttribute("name", "foobar");
      fourthChildChild.setValue("some text for grand-child");

      fourthChild.commit();

      assertTrue(fourthChildChild.isCommitted());
      assertTrue(fourthChild.isCommitted());
      assertEquals(fourthChildChild.getChild("blah"), null);
      assertEquals(fourthChild.getChild("example"), null);

      root.commit();
      validate(out.toString());
   }
}
