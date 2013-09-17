package org.simpleframework.xml.core;

import java.io.PrintWriter;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.strategy.TreeStrategy;
import org.simpleframework.xml.strategy.Type;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeBuilder;
import org.simpleframework.xml.stream.OutputNode;

@SuppressWarnings("unused")
public class CompositeListUnionTest extends TestCase {

   @Path("some/path")
   @ElementListUnion({
      @ElementList(entry="circle", type=Circle.class, inline=true),
      @ElementList(entry="square", type=Square.class, inline=true),
      @ElementList(entry="triangle", type=Triangle.class, inline=true)
   })
   private List<Object> EXAMPLE; 
   
   private static interface Shape {}
   
   private static class Circle implements Shape {
      private @Text double radius; 
   }
   
   private static class Square implements Shape {
      private @Text int width; 
   }
   
   private static class Triangle implements Shape {
      private @Text double height; 
   }
   
   private final static String SOURCE =
   "<parent>" +
   "  <circle>1.0</circle>" +
   "  <square>4</square>" +
   "  <triangle>5</triangle>" +
   "</parent>";
   
   private final static String LARGE_SOURCE =
   "<parent>" +
   "  <circle>1.0</circle>" +
   "  <square>4</square>" +
   "  <triangle>5</triangle>" +
   "  <circle>1.0</circle>" +
   "  <square>4</square>" +
   "  <triangle>5</triangle>" +
   "  <circle>1.0</circle>" +
   "  <square>4</square>" +
   "  <triangle>5</triangle>" +
   "  <circle>1.0</circle>" +
   "  <square>4</square>" +
   "  <triangle>5</triangle>" +
   "  <circle>1.0</circle>" +
   "  <square>4</square>" +
   "  <triangle>5</triangle>" +
   "  <circle>1.0</circle>" +
   "  <square>4</square>" +
   "  <triangle>5</triangle>" +
   "</parent>";

   public void testSimpleList() throws Exception {
      Context context = getContext();
      Group group = getGroup();
      Type type = new ClassType(CompositeListUnionTest.class);
      List<Shape> list = new ArrayList<Shape>();
      Expression expression = new PathParser("some/path", type, new Format());
      CompositeListUnion union = new CompositeListUnion(
            context,
            group,
            expression,
            type);
      InputNode node = NodeBuilder.read(new StringReader(SOURCE));
      
      InputNode circle = node.getNext();
      union.read(circle, list);
      
      InputNode square = node.getNext();
      union.read(square, list);
      
      InputNode triangle = node.getNext();
      union.read(triangle, list);
      
      assertEquals(list.get(0).getClass(), Circle.class);
      assertEquals(list.get(1).getClass(), Square.class);
      assertEquals(list.get(2).getClass(), Triangle.class);
      
      OutputNode output = NodeBuilder.write(new PrintWriter(System.out), new Format(3));
      OutputNode parent = output.getChild("parent");
      
      union.write(parent, list);
   }
   
   public void testLargeList() throws Exception {
      Context context = getContext();
      Group group = getGroup();
      Type type = new ClassType(CompositeListUnionTest.class);
      List<Shape> list = new ArrayList<Shape>();
      Expression expression = new PathParser("some/path", type, new Format());
      CompositeListUnion union = new CompositeListUnion(
            context,
            group,
            expression,
            type);
      InputNode node = NodeBuilder.read(new StringReader(LARGE_SOURCE));
      
      for(InputNode next = node.getNext(); next != null; next = node.getNext()) {
         union.read(next, list);
      }
      OutputNode output = NodeBuilder.write(new PrintWriter(System.err), new Format(3));
      OutputNode parent = output.getChild("parent");
      
      union.write(parent, list);
   }
   
   private static Group getGroup() throws Exception {
      Field field = CompositeListUnionTest.class.getDeclaredField("EXAMPLE");
      Annotation annotation = field.getAnnotation(ElementListUnion.class);
      Annotation path = field.getAnnotation(Path.class);
      return new GroupExtractor(
            new FieldContact(field, annotation, new Annotation[]{annotation, path}),
            annotation,
            new Format());
   }
   
   private static Context getContext() {
      return new Source(
            new TreeStrategy(),
            new Support(),
            new Session());
   }
}