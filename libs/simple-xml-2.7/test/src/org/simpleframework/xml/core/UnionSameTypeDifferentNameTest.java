package org.simpleframework.xml.core;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementUnion;

public class UnionSameTypeDifferentNameTest extends ValidationTestCase {
   
   private static final String USERNAME_SOURCE =
   "<optionalNameExample>"+
   "  <username>john.doe</username>"+
   "</optionalNameExample>";
   
   private static final String LOGIN_SOURCE =
   "<optionalNameExample>"+
   "  <login>john.doe</login>"+
   "</optionalNameExample>";
   
   private static final String ACCOUNT_SOURCE =
   "<optionalNameExample>"+
   "  <account>john.doe</account>"+
   "</optionalNameExample>";   
   
   @Root
   private static class OptionalNameExample {
      
      @ElementUnion({
         @Element(name="login"),
         @Element(name="name"),
         @Element(name="username", type=String.class),
         @Element(name="account")
      })      
      private String name;
      public String getName(){
         return name;
      }
      public void setName(String name){
         this.name = name;
      }
   }

   public void testOptionalName() throws Exception{
      Persister persister = new Persister();
      
      OptionalNameExample username = persister.read(OptionalNameExample.class, USERNAME_SOURCE);
      assertEquals(username.getName(), "john.doe");
      validate(persister, username);
      
      OptionalNameExample login = persister.read(OptionalNameExample.class, LOGIN_SOURCE);
      assertEquals(login.getName(), "john.doe");
      validate(persister, login);
      
      OptionalNameExample account = persister.read(OptionalNameExample.class, ACCOUNT_SOURCE);
      assertEquals(account.getName(), "john.doe");
      validate(persister, account);
   }
}
