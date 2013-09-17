package org.simpleframework.xml.core;

import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class NestedListTest extends ValidationTestCase {

   private static String SOURCE = "<testSuit>\r"
   + "<defaultTestScenario>\r"
   + "<testCase name='Test Case' description='abc'>\r"
   + "<step id='1' action='open' target='field id' value='value' />\r"
   + "<step id='2' action='type' target='field id' value='value' />\r"
   + "<step id='3' action='select' target='field id' value='value' />\r"
   + "<step id='4' action='clickAndWait' target='field id' value='value' />\r"
   + "<step id='5' action='assertTrue' target='field id' value='value' />\r"
   + "</testCase>\r"
   + "<testCase name='Test Case1' description='abc'>\r"
   + "<step id='1' action='open' target='field id' value='value' />\r"
   + "<step id='2' action='type' target='field id' value='value' />\r"
   + "<step id='3' action='select' target='field id' value='value' />\r"
   + "<step id='4' action='clickAndWait' target='field id' value='value' />\r"
   + "<step id='5' action='assertTrue' target='field id' value='value' />\r"
   + "</testCase>\r"
   + "</defaultTestScenario>\r"
   + "<testScenario name='Test Case1,Test Case3' description='abc'\r"
   + "includeDefault='true'>\r"
   + "<testCase name='Test Case1' description='abc'>\r"
   + "<step id='1' action='open' target='field id' value='value' />\r"
   + "<step id='2' action='type' target='field id' value='value' />\r"
   + "<step id='3' action='select' target='field id' value='value' />\r"
   + "<step id='4' action='clickAndWait' target='field id' value='value' />\r"
   + "<step id='5' action='assertTrue' target='field id' value='value' />\r"
   + "<step id='6' action='assertTrue' target='field id' value='value' />\r"
   + "</testCase>\r"
   + "<testCase name='Test Case3' description='abc'>\r"
   + "<step id='1' action='open' target='field id' value='value' />\r"
   + "<step id='2' action='type' target='field id' value='value' />\r"
   + "<step id='3' action='select' target='field id' value='value' />\r"
   + "<step id='4' action='clickAndWait' target='field id' value='value' />\r"
   + "<step id='5' action='assertTrue' target='field id' value='value' />\r"
   + "<step id='6' action='assertTrue' target='field id' value='value' />\r"
   + "<step id='7' action='assertTrue' target='field id' value='value' />\r"
   + "</testCase>\r"
   + "</testScenario>\r"
   + "<testScenario name='Test Case2,Test Case4' description='abc'>\r"
   + "<testCase name='Test Case2' description=''>\r"
   + "<step id='1' action='open' target='field id' value='value' />\r"
   + "<step id='2' action='type' target='field id' value='value' />\r"
   + "<step id='3' action='select' target='field id' value='value' />\r"
   + "<step id='4' action='clickAndWait' target='field id' value='value' />\r"
   + "<step id='5' action='assertTrue' target='field id' value='value' />\r"
   + "<step id='6' action='open' target='field id' value='value' />\r"
   + "<step id='7' action='type' target='field id' value='value' />\r"
   + "<step id='8' action='select' target='field id' value='value' />\r"
   + "<step id='9' action='clickAndWait' target='field id' value='value' />\r"
   + "<step id='10' action='assertTrue' target='field id' value='value' />\r"
   + "</testCase>\r"
   + "<testCase name='Test Case4' description=''>\r"
   + "<step id='1' action='open' target='field id' value='value' />\r"
   + "<step id='2' action='type' target='field id' value='value' />\r"
   + "<step id='3' action='select' target='field id' value='value' />\r"
   + "<step id='4' action='clickAndWait' target='field id' value='value' />\r"
   + "<step id='5' action='assertTrue' target='field id' value='value' />\r"
   + "</testCase>\r" + "</testScenario>\r" + "</testSuit>\r";

   @Root
   public static class DefaultTestScenario {

      @ElementList(inline = true)
      private List<TestCase> testCases;

      @Attribute(required = false)
      private String includeDefault = Boolean.toString(false);

      public boolean getIncludeDefault() {
         return Boolean.parseBoolean(includeDefault);
      }

      public List<TestCase> getTestCases() {
         return testCases;
      }
   }

   @Root
   public static class TestScenario {

      @Element(required = false)
      private String uRLs;

      @Attribute
      private String name;

      @Attribute
      private String description;

      @ElementList(inline = true)
      private List<TestCase> testCases;

      @Attribute(required = false)
      private String includeDefault = Boolean.toString(false);

      public boolean getIncludeDefault() {
         return Boolean.parseBoolean(includeDefault);
      }

      public List<TestCase> getTestCases() {
         return testCases;
      }

      public String getURLs() {
         return uRLs;
      }

      public String getName() {
         return name;
      }

      public String getDescription() {
         return description;
      }

      @Override
      public String toString() {
         return getName();
      }
   }

   @Root
   public final static class TestCase {

      @ElementList(inline = true)
      private List<Step> steps;

      @Attribute
      private String name;

      @Attribute
      private String description;

      @Attribute(required = false)
      private String addBefore;

      public String getAddBefore() {
         return addBefore;
      }

      public String getName() {
         return name;
      }

      public String getDescription() {
         return description;
      }

      public List<Step> getSteps() {
         return steps;
      }

      @Override
      public String toString() {
         return getName();
      }
   }

   @Root
   public final static class Step {

      @Attribute
      private int id;

      @Attribute
      private String action;

      @Attribute
      private String target;

      @Attribute
      private String value;

      public int getId() {
         return id;
      }

      public String getAction() {
         return action;
      }

      public String getTarget() {
         return target;
      }

      public String getValue() {
         return value;
      }

      @Override
      public String toString() {
         return Integer.toString(getId());
      }
   }

   @Root
   public static class TestSuit {

      @Element
      private DefaultTestScenario defaultTestScenario;

      @ElementList(inline = true, entry = "testScenario")
      private List<TestScenario> testScenario;

      public DefaultTestScenario getDefaultTestScenario() {
         return defaultTestScenario;
      }

      public List<TestScenario> getTestScenario() {
         return testScenario;

      }
   }
   
   public void testNestedList() throws Exception {
      Persister persister = new Persister();
      TestSuit suit = persister.read(TestSuit.class, SOURCE);
      persister.write(suit,System.out);
   }
}
