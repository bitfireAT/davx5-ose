package org.simpleframework.xml.stream;

import junit.framework.TestCase;

public class StrategyTest extends TestCase {
   
   public void testHyphenStrategy() {
      Style strategy = new HyphenStyle();
      
      System.err.println(strategy.getElement("Base64Encoder"));
      System.err.println(strategy.getElement("Base64_Encoder"));
      System.err.println(strategy.getElement("Base64___encoder"));
      System.err.println(strategy.getElement("base64--encoder"));
      System.err.println(strategy.getElement("Base64encoder"));
      System.err.println(strategy.getElement("_Base64encoder"));
      System.err.println(strategy.getElement("__Base64encoder"));
      System.err.println(strategy.getElement("URLList"));
      System.err.println(strategy.getElement("__Base64encoder"));
      System.err.println(strategy.getElement("Base_64_Encoder"));
      System.err.println(strategy.getElement("base_64_encoder"));
   }

   public void testCamelCaseStrategy() {
      Style strategy = new CamelCaseStyle();
      
      System.err.println(strategy.getElement("Base64Encoder"));
      System.err.println(strategy.getElement("Base64_Encoder"));
      System.err.println(strategy.getElement("Base64___encoder"));
      System.err.println(strategy.getElement("base64--encoder"));
      System.err.println(strategy.getElement("Base64encoder"));
      System.err.println(strategy.getElement("_Base64encoder"));
      System.err.println(strategy.getElement("__Base64encoder"));
      System.err.println(strategy.getElement("URLList"));
      System.err.println(strategy.getElement("__Base64encoder"));
      System.err.println(strategy.getElement("Base_64_Encoder"));
      System.err.println(strategy.getElement("base_64_encoder"));
   }
}
