package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Order;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class PathWithOrderedAttributesTest extends ValidationTestCase {
   
   @Root
   @Order(attributes={"group/@processor", "group/@os"})
   private static class ServerDeployment {
      @Attribute
      @Path("group")
      private OperatingSystem os;
      @Attribute
      @Path("group")
      private Processor processor;
      @Element
      @Path("group/server[1]/details")
      private Server primary;
      @Element
      @Path("group/server[2]/details")
      private Server secondary;
      public Processor getProcessor() {
         return processor;
      }
      public void setProcessor(Processor processor) {
         this.processor = processor;
      }
      public OperatingSystem getOS() {
         return os;
      }
      public void setOS(OperatingSystem os) {
         this.os = os;
      }
      public Server getPrimary() {
         return primary;
      }
      public void setPrimary(Server primary) {
         this.primary = primary;
      }
      public Server getSecondary() {
         return secondary;
      }
      public void setSecondary(Server secondary) {
         this.secondary = secondary;
      }
   }

   @Root
   private static class Server {
      @Attribute
      private String host;
      @Attribute
      private int port; 
      public Server(@Attribute(name="host") String host,
                     @Attribute(name="port") int port)
      {
         this.host = host;
         this.port = port;
      }
      
      public String getHost() {
         return host;
      }      
      public int getPort() {
         return port;
      }

   }

   private static enum OperatingSystem {
      WINDOWS,
      LINUX,
      SOLARIS
   }
   
   private static enum Processor {
      AMD,
      INTEL,
      SPARC
   }
   
   public void testAttributeOrder() throws Exception {
      Persister persister = new Persister();
      ServerDeployment deployment = new ServerDeployment();
      Server primary = new Server("host1.domain.com", 3487);
      Server secondary = new Server("host2.domain.com", 3487);
      StringWriter writer = new StringWriter();
      deployment.setPrimary(primary);
      deployment.setSecondary(secondary);
      deployment.setOS(OperatingSystem.LINUX);
      deployment.setProcessor(Processor.INTEL);
      persister.write(deployment, writer);
      System.out.println(writer.toString());
      ServerDeployment recovered = persister.read(ServerDeployment.class, writer.toString());
      assertEquals(recovered.getOS(), deployment.getOS());
      assertEquals(recovered.getProcessor(), deployment.getProcessor());
      assertEquals(recovered.getPrimary().getHost(), deployment.getPrimary().getHost());
      assertEquals(recovered.getPrimary().getPort(), deployment.getPrimary().getPort());
      assertEquals(recovered.getSecondary().getHost(), deployment.getSecondary().getHost());
      assertEquals(recovered.getSecondary().getPort(), deployment.getSecondary().getPort());
      assertElementHasAttribute(writer.toString(), "/serverDeployment/group", "os", "LINUX");
      assertElementHasAttribute(writer.toString(), "/serverDeployment/group", "processor", "INTEL");
      assertElementHasAttribute(writer.toString(), "/serverDeployment/group/server[1]/details/primary", "host", "host1.domain.com");
      assertElementHasAttribute(writer.toString(), "/serverDeployment/group/server[1]/details/primary", "port", "3487");
      assertElementHasAttribute(writer.toString(), "/serverDeployment/group/server[2]/details/secondary", "host", "host2.domain.com");
      assertElementHasAttribute(writer.toString(), "/serverDeployment/group/server[2]/details/secondary", "port", "3487");
      validate(deployment, persister);
   }



}
