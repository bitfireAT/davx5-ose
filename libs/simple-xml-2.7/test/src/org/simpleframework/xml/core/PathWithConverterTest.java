package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.convert.AnnotationStrategy;
import org.simpleframework.xml.convert.Convert;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.stream.CamelCaseStyle;
import org.simpleframework.xml.stream.Format;
import org.simpleframework.xml.stream.HyphenStyle;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.OutputNode;
import org.simpleframework.xml.stream.Style;

public class PathWithConverterTest extends ValidationTestCase {
   
   @Default  
   private static class ServerDetailsReference {
      
      @Path("server-details/host[1]")
      @Convert(ServerDetailsConverter.class)
      private ServerDetails primary;
      
      @Path("server-details/host[2]")
      @Convert(ServerDetailsConverter.class)
      private ServerDetails secondary;
      
      public ServerDetailsReference(
            @Element(name="primary") ServerDetails primary,
            @Element(name="secondary") ServerDetails secondary) 
      {
         this.primary = primary;
         this.secondary = secondary;
      }
      public ServerDetails getPrimary(){
         return primary;
      }
      public ServerDetails getSecondary(){
         return secondary;
      }     
   }
   
   private static class ServerDetailsConverter implements org.simpleframework.xml.convert.Converter<ServerDetails> {
      public ServerDetails read(InputNode node) throws Exception {
         String host = node.getAttribute("host").getValue();
         String port = node.getAttribute("port").getValue();
         String name = node.getValue();
         return new ServerDetails(host, Integer.parseInt(port), name);
      }
      public void write(OutputNode node, ServerDetails value) throws Exception {
         node.setAttribute("host", value.host);
         node.setAttribute("port", String.valueOf(value.port));
         node.setValue(value.name);
      }  
   }
   
   private static class ServerDetails {
      private final String host;
      private final int port;
      private final String name;
      public ServerDetails(String host, int port, String name){
         this.host = host;
         this.name = name;
         this.port = port;
      }
      public String getHost() {
         return host;
      }
      public String getName(){
         return name;
      }
      public int getPort() {
         return port;
      }
   }
   
   public void testConverterWithPathInHyphenStyle() throws Exception {
      Style style = new HyphenStyle();
      Format format = new Format(style);
      Strategy strategy = new AnnotationStrategy();
      Persister persister = new Persister(strategy, format);
      ServerDetails primary = new ServerDetails("host1.blah.com", 4567, "PRIMARY");
      ServerDetails secondary = new ServerDetails("host2.foo.com", 4567, "SECONDARY");
      ServerDetailsReference reference = new ServerDetailsReference(primary, secondary);
      StringWriter writer = new StringWriter();
      persister.write(reference, writer);
      System.out.println(writer);
      ServerDetailsReference recovered = persister.read(ServerDetailsReference.class, writer.toString());
      assertEquals(recovered.getPrimary().getHost(), reference.getPrimary().getHost());
      assertEquals(recovered.getPrimary().getPort(), reference.getPrimary().getPort());
      assertEquals(recovered.getPrimary().getName(), reference.getPrimary().getName());
      assertEquals(recovered.getSecondary().getHost(), reference.getSecondary().getHost());
      assertEquals(recovered.getSecondary().getPort(), reference.getSecondary().getPort());
      assertEquals(recovered.getSecondary().getName(), reference.getSecondary().getName());
   }

   public void testConverterWithPathInCamelStyle() throws Exception {
      Style style = new CamelCaseStyle();
      Format format = new Format(style);
      Strategy strategy = new AnnotationStrategy();
      Persister persister = new Persister(strategy, format);
      ServerDetails primary = new ServerDetails("host1.blah.com", 4567, "PRIMARY");
      ServerDetails secondary = new ServerDetails("host2.foo.com", 4567, "SECONDARY");
      ServerDetailsReference reference = new ServerDetailsReference(primary, secondary);
      StringWriter writer = new StringWriter();
      persister.write(reference, writer);
      System.out.println(writer);
      ServerDetailsReference recovered = persister.read(ServerDetailsReference.class, writer.toString());
      assertEquals(recovered.getPrimary().getHost(), reference.getPrimary().getHost());
      assertEquals(recovered.getPrimary().getPort(), reference.getPrimary().getPort());
      assertEquals(recovered.getPrimary().getName(), reference.getPrimary().getName());
      assertEquals(recovered.getSecondary().getHost(), reference.getSecondary().getHost());
      assertEquals(recovered.getSecondary().getPort(), reference.getSecondary().getPort());
      assertEquals(recovered.getSecondary().getName(), reference.getSecondary().getName());
   }
}
