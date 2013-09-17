package org.simpleframework.xml.strategy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeMap;
import org.simpleframework.xml.stream.OutputNode;

public class CommentTest extends ValidationTestCase {
   
   @Retention(RetentionPolicy.RUNTIME)
   private static @interface Comment {
      public String value();
   }
   
   @Root
   @Default
   private static class CommentExample {
      @Comment("This represents the name value")
      private String name;
      @Comment("This is a value to be used")
      private String value;
      @Comment("Yet another comment")
      private Double price;
   }
   
   private static class CommentVisitor implements Visitor {
      public void read(Type type, NodeMap<InputNode> node) throws Exception {}
      public void write(Type type, NodeMap<OutputNode> node) throws Exception {
         if(!node.getNode().isRoot()) {
            Comment comment = type.getAnnotation(Comment.class);
            if(comment != null) {
               node.getNode().setComment(comment.value());
            }
         }
      }
   }
   
   public void testComment() throws Exception {
      Visitor visitor = new CommentVisitor();
      Strategy strategy = new VisitorStrategy(visitor);
      Persister persister = new Persister(strategy);
      CommentExample example = new CommentExample();
      
      example.name = "Some Name";
      example.value = "A value to use";
      example.price = 9.99;
      
      persister.write(example, System.out);
   }

}
