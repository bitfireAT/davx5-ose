package org.simpleframework.xml.core;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import junit.framework.TestCase;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

public class XPathTest extends TestCase {
	
   private static final String SOURCE =
   "<inventory>\n"+
   "    <book year='2000'>\n"+
   "        <title>Snow Crash</title>\n"+
   "        <author>Neal Stephenson</author>\n"+
   "        <publisher>Spectra</publisher>\n"+
   "        <isbn>0553380958</isbn>\n"+
   "        <price>14.95</price>\n"+
   "    </book>\n"+
   "    <book year='2005'>\n"+
   "        <title>Burning Tower</title>\n"+
   "        <author>Larry Niven</author>\n"+
   "        <author>Jerry Pournelle</author>\n"+
   "        <publisher>Pocket</publisher>\n"+
   "        <isbn>0743416910</isbn>\n"+
   "        <price>5.99</price>\n"+
   "    </book>\n"+
   "    <book year='1995'>\n"+
   "        <title>Zodiac</title>\n"+
   "        <author>Neal Stephenson</author>\n"+
   "        <publisher>Spectra</publisher>\n"+
   "        <isbn>0553573862</isbn>\n"+
   "        <price>7.50</price>\n"+
   "    </book>\n"+
   "</inventory>\n";		
	
	public void testXPath() throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true); // never forget this!
		DocumentBuilder builder = factory.newDocumentBuilder();
		byte[] source = SOURCE.getBytes("UTF-8");
		InputStream stream = new ByteArrayInputStream(source);
		Document doc = builder.parse(stream);
		XPathFactory xpathFactory = XPathFactory.newInstance(); 
		XPath xpath = xpathFactory.newXPath(); 
		XPathExpression expr = xpath.compile("inventory/book[1]/@year"); 

	    Object result = expr.evaluate(doc, XPathConstants.NODESET);
	    NodeList nodes = (NodeList) result;
	    for (int i = 0; i < nodes.getLength(); i++) {
	        System.out.println(nodes.item(i).getNodeValue()); 
	    }		
	}

}
