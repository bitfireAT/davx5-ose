package org.simpleframework.xml.core;

import junit.framework.TestCase;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

public class EnumTest extends TestCase {
    
    private static final String SOURCE = 
    "<enumBug>\n"+
    "  <type>A</type>\n"+
    "</enumBug>";
    
    private static final String LIST = 
    "<enumVariableArgumentsBug>\n"+
    "  <types>A,B,A,A</types>\n"+
    "</enumVariableArgumentsBug>";
    
    enum PartType {
        A,
        B
    }

    @Root
    public static class EnumBug {

        @Element
        private PartType type;

        public EnumBug(@Element(name="type") PartType type) {
           this.type = type;
        }

        public PartType getType() {
           return type;        
        }
    }
    
    @Root
    public static class EnumVariableArgumentsBug {

        @Element
        private PartType[] types;

        public EnumVariableArgumentsBug(@Element(name="types") PartType... types) {
           this.types = types;
        }

        public PartType[] getTypes() {
           return types;        
        }
    }
    
    
    public void testEnum() throws Exception {
        Serializer serializer = new Persister();
        EnumBug bug = serializer.read(EnumBug.class, SOURCE);
     
        assertEquals(bug.getType(), PartType.A);
    }
    
    public void testVargsEnum() throws Exception {
        Serializer serializer = new Persister();
        EnumVariableArgumentsBug bug = serializer.read(EnumVariableArgumentsBug.class, LIST);
     
        assertEquals(bug.getTypes()[0], PartType.A);
    }
}
