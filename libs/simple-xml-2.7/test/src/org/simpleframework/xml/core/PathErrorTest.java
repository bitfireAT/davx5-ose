package org.simpleframework.xml.core;

import java.io.StringWriter;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

public class PathErrorTest extends TestCase{

   @Root(name="header")
   public static class TradeHeader {

      @Element(name = "sourceSystem")
      @Path("tradeIdentifier/tradeKey")
      @Namespace
      private String tradeKeySourceSystem = "VALUE";

      @Attribute(name = "id")
      @Path("book")
      private String bookId = "BOOK";

      @Element(name = "code")
      @Path("book/identifier")
      @Namespace
      private String bookCode = "code";

      @Element(name = "sourceSystem")
      @Path("book/identifier")
      @Namespace
      private String bookSourceSystem = "VALUE";

      @Element(name = "type")
      @Path("book/identifier")
      @Namespace
      private String bookType = "SHORT_NAME";

      @Element(name = "role")
      @Path("book")
      @Namespace
      private String bookRole = "VALUE";

      @Attribute(name = "id")
      @Path("trader")
      private String traderId = "TID";

      @Element(name = "code")
      @Path("trader/identifier")
      @Namespace
      private String traderCode = "tCode";

      @Element(name = "sourceSystem")
      @Path("trader/identifier")
      @Namespace
      private String traderSourceSystem = "VALUE";

      @Element(name = "type")
      @Path("trader/identifier")
      @Namespace
      private String traderType = "SHORT_NAME";

      @Element(name = "role")
      @Path("trader")
      @Namespace
      private String traderRole = "VALUE";
   }
   
   public void testRepeat() throws Exception {
      Serializer serializer = new Persister();
      StringWriter writer = new StringWriter();
      TradeHeader header = new TradeHeader();
      serializer.write(header, writer);

      String data = writer.getBuffer().toString();
      
      System.out.println(data);
   }
}
