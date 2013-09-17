package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.ElementListUnion;

public class UnionListConstructorInjectionTest extends ValidationTestCase {
   
   @Root
   private static class AccessControl{
      @ElementListUnion({
         @ElementList(entry="user", inline=true, type=UserIdentity.class),
         @ElementList(entry="admin", inline=true, type=AdministratorIdentity.class)
      })      
      private final List<Identity> users;
      public AccessControl(@ElementList(name="user") List<Identity> users){
        this.users = users;
      }
      public List<Identity> getUsers(){
         return users;
      }
   }
   @Root
   private static class Identity{
      private final String name;
      public Identity(@Element(name="name") String name){
         this.name = name;
      }
      @ElementUnion({
         @Element(name="login"),
         @Element(name="name"),
         @Element(name="user")
      })
      public String getName(){
         return name;
      }
      public boolean isAdministrator(){
         return false;
      }
   }
   @Root
   public static class UserIdentity extends Identity{
      @Element
      private final String password;
      public UserIdentity(@Element(name="name") String name, @Element(name="password") String password){
         super(name);
         this.password = password;
      }
      public String getPassword(){
         return password;
      }      
   }
   @Root
   public static class AdministratorIdentity extends UserIdentity{
      public AdministratorIdentity(@Element(name="name") String name, @Element(name="password") String password){
         super(name, password);
      }
      public boolean isAdministrator(){
         return true;
      }
   }
   public void testListInjection() throws Exception{
      Persister persister = new Persister();
      LinkedList<Identity> users = new LinkedList<Identity>();
      users.add(new UserIdentity("a", "pass1"));
      users.add(new UserIdentity("b", "pass2"));
      users.add(new UserIdentity("c", "pass3"));
      users.add(new UserIdentity("d", "pass4"));
      users.add(new UserIdentity("e", "pass5"));
      users.add(new AdministratorIdentity("root", "password1234"));
      AccessControl control = new AccessControl(users);
      StringWriter writer = new StringWriter();
      persister.write(control, writer);
      String text = writer.toString();
      System.out.println(text);
      AccessControl deserialized = persister.read(AccessControl.class, text);
      assertEquals(deserialized.getUsers().get(0).getName(), "a");
      assertEquals(deserialized.getUsers().get(0).getClass(), UserIdentity.class);
      assertEquals(UserIdentity.class.cast(deserialized.getUsers().get(0)).getPassword(), "pass1");
      assertEquals(deserialized.getUsers().get(1).getName(), "b");
      assertEquals(deserialized.getUsers().get(1).getClass(), UserIdentity.class);
      assertEquals(UserIdentity.class.cast(deserialized.getUsers().get(1)).getPassword(), "pass2");
      assertEquals(deserialized.getUsers().get(2).getName(), "c");
      assertEquals(deserialized.getUsers().get(2).getClass(), UserIdentity.class);
      assertEquals(UserIdentity.class.cast(deserialized.getUsers().get(2)).getPassword(), "pass3");
      assertEquals(deserialized.getUsers().get(3).getName(), "d");
      assertEquals(deserialized.getUsers().get(3).getClass(), UserIdentity.class);
      assertEquals(UserIdentity.class.cast(deserialized.getUsers().get(3)).getPassword(), "pass4");
      assertEquals(deserialized.getUsers().get(4).getName(), "e");
      assertEquals(deserialized.getUsers().get(4).getClass(), UserIdentity.class);
      assertEquals(UserIdentity.class.cast(deserialized.getUsers().get(4)).getPassword(), "pass5");
      assertEquals(deserialized.getUsers().get(5).getName(), "root");
      assertEquals(deserialized.getUsers().get(5).getClass(), AdministratorIdentity.class);
      assertEquals(UserIdentity.class.cast(deserialized.getUsers().get(5)).getPassword(), "password1234");
      validate(persister, deserialized);
   }
}
