package org.simpleframework.xml.core;

import java.io.Serializable;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

public class SimpleConstructorInjectionTest extends TestCase {

   public void testConstructorInjection() throws Exception{
      String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<MessageWrapper>" +
            "<necessary>test</necessary>" +
            "<optional/>" +
            "</MessageWrapper>";
      Serializer serializer = new Persister();
      
      Message example = serializer.read(Message.class, xml);
      System.out.println("message: "+example.getOptional());
   }
   
   
   @Root
   public static class Message implements Serializable{
      @Element
      private String necessary;

      @Element(required=false)
      private String optional;
      
      public Message(@Element(name="necessary") String necessary){
         this.necessary = necessary;
      }     
      public String getNecessary() {
         return necessary;
      }
      public void setNecessary(String necessary) {
         this.necessary = necessary;
      }
      public String getOptional() {
         return optional;
      }
      public void setOptional(String optional) {
         this.optional = optional;
      }
   }
}
