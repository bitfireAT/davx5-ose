package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class ConstructorParameterMatchTest extends ValidationTestCase {

   @Root
   public static class BoardStateMove {

      @Attribute
      public final String from;
      
      @Attribute
      public final String to;
      
      @Attribute
      public final String fromPiece;
      
      @Attribute(required=false)
      public final String toPiece;
      
      @Attribute
      public final String moveDirection;
      
      public BoardStateMove(
            @Attribute(name="from") String from,
            @Attribute(name="to") String to,
            @Attribute(name="fromPiece") String fromPiece,
            @Attribute(name="toPiece") String toPiece,
            @Attribute(name="moveDirection") String moveDirection)
      {
         this.moveDirection = moveDirection;
         this.fromPiece = fromPiece;
         this.toPiece = toPiece;
         this.from = from;
         this.to = to;
      }
   }
   
   public void testParameterMatch() throws Exception {
      Persister p = new Persister();
      BoardStateMove m = new BoardStateMove("A5", "A6", "KING", null, "NORTH");
      StringWriter w = new StringWriter();
      p.write(m, w);
      String s = w.toString();
      BoardStateMove x = p.read(BoardStateMove.class, s);
      assertEquals(m.from, x.from);
      assertEquals(m.to, x.to);
      assertEquals(m.fromPiece, x.fromPiece);
      assertEquals(m.toPiece, x.toPiece);
      assertEquals(m.moveDirection, x.moveDirection);
   }

}
