package org.simpleframework.xml.core;

import java.util.LinkedList;
import java.util.List;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.strategy.CycleStrategy;
import org.simpleframework.xml.strategy.Strategy;

public class UnionInlineListWitinPathTest extends ValidationTestCase {

   @Default
   private static class Department {
      @Path("employees")
      @ElementListUnion({
         @ElementList(entry="graduate", inline=true, type=Graduate.class),
         @ElementList(entry="assistant", inline=true, type=Assistant.class),
         @ElementList(entry="manager", inline=true, type=Manager.class)     
      }) 
      private List<Employee> employees = new LinkedList<Employee>();
      private String name;
      public Department(@Element(name="name") String name) {
         this.name = name;
      }  
      public List<Employee> getEmployees(){
         return employees;
      }
      public String getName(){
         return name;
      }
   }
   
   @Default
   private abstract static class Employee{
      private final Department department;
      private final String name;
      protected Employee(Department department, String name){
         this.department = department;
         this.name = name;
      }
      public Department getDepartment(){
         return department;
      }
      public boolean isManager(){
         return false;
      }
   }
   
   @Default
   private static class Graduate extends Employee {      
      public Graduate(@Element(name="department") Department department, @Element(name="name") String name){
         super(department, name);
      }
   }
   
   @Default
   private static class Assistant extends Employee {      
      public Assistant(@Element(name="department") Department department, @Element(name="name") String name){
         super(department, name);
      }
   }   
   
   @Default
   private static class Manager extends Employee {
      @Path("subordinates")
      @ElementListUnion({
         @ElementList(entry="graduate", inline=true, type=Graduate.class),
         @ElementList(entry="assistant", inline=true, type=Assistant.class)        
      })      
      private List<Employee> list = new LinkedList<Employee>();
      public Manager(@Element(name="department") Department department, @Element(name="name") String name){
         super(department, name);
      }
      public List<Employee> getEmployees(){
         return list;
      }
      public boolean isManager() {
         return true;
      }
   }
   
   public void testListWithPath() throws Exception {
      Strategy strategy = new CycleStrategy();
      Persister persister = new Persister(strategy);
      Department department = new Department("Human Resources");
      Manager manager = new Manager(department, "Tom");
      Graduate graduate = new Graduate(department, "Dick");
      Assistant assistant = new Assistant(department, "Harry");
      department.getEmployees().add(manager);
      department.getEmployees().add(graduate);
      department.getEmployees().add(assistant);
      manager.getEmployees().add(graduate);
      manager.getEmployees().add(assistant);
      persister.write(department, System.out);
   }
}
