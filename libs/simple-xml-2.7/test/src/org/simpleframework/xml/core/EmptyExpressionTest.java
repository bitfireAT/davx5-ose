package org.simpleframework.xml.core;

import junit.framework.TestCase;

import org.simpleframework.xml.strategy.Type;
import org.simpleframework.xml.stream.Format;

public class EmptyExpressionTest extends TestCase {

	public void testEmptyPath() throws Exception {
		Format format = new Format();
		Type type = new ClassType(EmptyExpressionTest.class);
		Expression path = new PathParser(".", type, format);
		Expression empty = new EmptyExpression(format);
		
		assertEquals(path.isEmpty(), empty.isEmpty());
		assertEquals(path.isAttribute(), empty.isAttribute());
		assertEquals(path.isPath(), empty.isPath());
		assertEquals(path.getAttribute("a"), empty.getAttribute("a"));
		assertEquals(path.getElement("a"), empty.getElement("a"));
		assertEquals(path.getPath(), empty.getPath());
	}
}
