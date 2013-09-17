package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class ReplaceThisTest extends ValidationTestCase {
    
    @Root
    public static class RealParent {
        
        @Element
        private ReplaceThisParent inner;
        
        public RealParent() {
            this.inner = new ReplaceThisParent();
        }
        
        public RealParent(Set<String> children) {
            this.inner = new ReplaceThisParent(children);
        }
        
        public ReplaceThisParent getInner() {
            return inner;
        }
    }
    
    @Root
    public static class ReplaceThisParent {

        @ElementList(required = false)
        Set<String> children;
        
        public ReplaceThisParent() {
            this.children = new TreeSet<String>();
        }
        
        public ReplaceThisParent(Set<String> children) {
           this.children = children;
        }

        @Replace
        private ReplaceThisParent replaceParent() {
           return new ReplaceThisParent(null);
        }

        public void setChildren(Set<String> children) {
            this.children=children;
        }

        public Set<String> getChildren() {
            return children;
        }
    }
    
    public void testReplaceParent() throws Exception {
        Persister persister = new Persister();
        Set<String> children = new HashSet<String>();
        RealParent parent = new RealParent(children);
        
        children.add("Tom");
        children.add("Dick");
        children.add("Harry");
        
        StringWriter writer = new StringWriter();
        persister.write(parent, writer);
        String text = writer.toString();
        
        System.out.println(text);
        
        assertEquals(text.indexOf("Tom"), -1);
        assertEquals(text.indexOf("Dick"), -1);
        assertEquals(text.indexOf("Harry"), -1);
        
        validate(persister, parent);
    }
}
