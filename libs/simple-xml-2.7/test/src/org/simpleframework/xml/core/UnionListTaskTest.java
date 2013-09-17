package org.simpleframework.xml.core;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.ValidationTestCase;

public class UnionListTaskTest extends ValidationTestCase {
   
   public static interface Operation {
      
      public void execute();
   }
   
   @Default
   public static class Delete implements Operation {

      private File file;
      
      public Delete(@Element(name="file") File file) {
         this.file = file;
      }
      
      public void execute() {
         file.delete();
      }
   }
   
   @Default
   public static class MakeDirectory implements Operation {

      private File path;
      
      private MakeDirectory(@Element(name="path") File path) {
         this.path = path;
      }
      
      public void execute() {
         path.mkdirs();
      }
   }
   
   @Default
   public static class Move implements Operation {
      
      private File source;
      private File destination;
      
      public Move(
            @Element(name="source") File source,
            @Element(name="destination") File destination)
      {
         this.source = source;
         this.destination = destination;
      }
      
      public void execute() {
         source.renameTo(destination);
      }
   }
   
   @Root
   private static class Task {
      
      @ElementListUnion({
         @ElementList(entry="delete", inline=true, type=Delete.class),
         @ElementList(entry="mkdir", inline=true, type=MakeDirectory.class),
         @ElementList(entry="move", inline=true, type=Move.class)
      })
      private List<Operation> operations;
      
      @Attribute
      private String name;
      
      public Task(@Attribute(name="name") String name) {
         this.operations = new LinkedList<Operation>();
         this.name = name;
      }
      
      public void add(Operation operation) {
         operations.add(operation);
      }
      
      public void execute() {
         for(Operation operation : operations) {
            operation.execute();
         }
      }
   }

   public void testUnion() throws Exception {
      Persister persister = new Persister();
      Task task = new Task("setup");
      task.add(new Delete(new File("C:\\workspace\\classes")));
      task.add(new MakeDirectory(new File("C:\\workspace\\classes")));
      task.add(new Move(new File("C:\\worksace\\classes"), new File("C:\\workspace\\build")));
      persister.write(task, System.out);
      validate(persister, task);
   }
}
