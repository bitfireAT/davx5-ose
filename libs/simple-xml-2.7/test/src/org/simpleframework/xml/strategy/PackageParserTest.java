package org.simpleframework.xml.strategy;


import java.net.URI;
import java.util.HashMap;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.w3c.dom.Node;

public class PackageParserTest extends TestCase {
   
   private static final int ITERATIONS = 100000;
   
   /*
    * javax.xml.namespace.NamespaceContext.getNamespaceURI(String prefix)
    * 
    * e.g
    * 
    * String reference = context.getNamespaceURI("class")
    * Class type = parser.parse(reference);
    * 
    * <element xmlns:class='http://util.java/ArrayList'>
    *    <name>name</name>
    *    <value>value</value>
    * </element> 
    * 
    */
   public void testParser() throws Exception {
      assertEquals("http://util.java/HashMap", parse(HashMap.class));
      assertEquals("http://simpleframework.org/xml/Element", parse(Element.class));
      assertEquals("http://simpleframework.org/xml/ElementList", parse(ElementList.class));
      assertEquals("http://w3c.org/dom/Node", parse(Node.class));
      assertEquals("http://simpleframework.org/xml/strategy/PackageParser", parse(PackageParser.class));
      
      assertEquals(HashMap.class, revert("http://util.java/HashMap"));
      assertEquals(Element.class, revert("http://simpleframework.org/xml/Element"));
      assertEquals(ElementList.class, revert("http://simpleframework.org/xml/ElementList"));
      assertEquals(Node.class, revert("http://w3c.org/dom/Node"));
      assertEquals(PackageParser.class, revert("http://simpleframework.org/xml/strategy/PackageParser"));
      
      long start = System.currentTimeMillis();
      for(int i = 0; i < ITERATIONS; i++) {
         fastParse(ElementList.class);
      }
      long fast = System.currentTimeMillis() - start;
      start = System.currentTimeMillis();
      for(int i = 0; i < ITERATIONS; i++) {
         parse(ElementList.class);
      }
      long normal = System.currentTimeMillis() - start;
      System.out.printf("fast=%sms normal=%sms diff=%s%n", fast, normal, normal / fast);
   }
   
   public String fastParse(Class type) throws Exception {
      return new PackageParser().parse(type);
   }
   
   public String parse(Class type) throws Exception {
      return new PackageParser().parse(type);
   }
   
   public Class revert(String type) throws Exception {
      return new PackageParser().revert(type);
   }
}