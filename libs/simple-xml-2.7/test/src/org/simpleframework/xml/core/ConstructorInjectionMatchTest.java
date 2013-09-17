package org.simpleframework.xml.core;

import java.io.StringWriter;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

/**
 * Created by IntelliJ IDEA.
 * User: e03229
 * Date: 10/11/10
 * Time: 10:52
 * To change this template use File | Settings | File Templates.
 */
public class ConstructorInjectionMatchTest extends TestCase {

    @Root(name = "root")
    private static class RootElement {

        @Element(name = "one")
        private final SimpleElementOne one;

        public RootElement(@Element(name = "one") SimpleElementOne one) {
            this.one = one;
        }
    }

    private static class SimpleElementOne {
        @Element(name = "two")
        private final SimpleElementTwo two;

        public SimpleElementOne(@Element(name = "two") SimpleElementTwo two) {
            this.two = two;
        }
        
        public SimpleElementOne(SimpleElementTwo two, int length) {
           this.two = two;
        }
    }

    private static class SimpleElementTwo {
        @Attribute(name = "value")
        private final String value;

        public SimpleElementTwo(@Attribute(name = "value") String value) {
            this.value = value;
        }

    }

    public void testConstructorInjection() throws Exception {
        SimpleElementTwo two = new SimpleElementTwo("val");
        SimpleElementOne one = new SimpleElementOne(two);
        RootElement root = new RootElement(one);

        Serializer serializer = new Persister();
        StringWriter output = new StringWriter();
        serializer.write(root, output);
        System.out.println(output.toString());
        serializer.read(RootElement.class, output.toString());
    }
}
