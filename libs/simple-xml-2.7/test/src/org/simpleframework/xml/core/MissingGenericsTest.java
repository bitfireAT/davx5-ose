package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.TestCase;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementMap;
import org.simpleframework.xml.Root;

public class MissingGenericsTest extends TestCase {
   
    @Root
    private static class MissingGenerics {       
       
        @SuppressWarnings("unchecked")
        @ElementMap(keyType=String.class, valueType=String.class)
        private Map map = new HashMap();
       
        @SuppressWarnings("unchecked")
        @ElementList(type=String.class)
        private List list = new ArrayList();
       
        @SuppressWarnings("unchecked")
        public Map getMap() {
            return map;
        }
       
        @SuppressWarnings("unchecked")
        public List getList() {
            return list;
        }
    }
   
    @SuppressWarnings("unchecked")
    public void testMissingGenerics() throws Exception {
        MissingGenerics example = new MissingGenerics();
        Persister persister = new Persister();
       
        Map map = example.getMap();
       
        map.put("a", "A");
        map.put("b", "B");
        map.put("c", "C");
        map.put("d", "D");
        map.put("e", "E");
       
        List list = example.getList();
       
        list.add("1");
        list.add("2");
        list.add("3");
        list.add("4");
        list.add("5");
       
       
        StringWriter out = new StringWriter();
        persister.write(example, out);
        String text = out.toString();
       
        MissingGenerics recovered = persister.read(MissingGenerics.class, text);
       
        assertEquals(recovered.getMap().size(), 5);
        assertEquals(recovered.getMap().get("a"), "A");
        assertEquals(recovered.getMap().get("b"), "B");
        assertEquals(recovered.getMap().get("c"), "C");
        assertEquals(recovered.getMap().get("d"), "D");
        assertEquals(recovered.getMap().get("e"), "E");
        assertTrue(recovered.getList().contains("1"));
        assertTrue(recovered.getList().contains("2"));
        assertTrue(recovered.getList().contains("3"));
        assertTrue(recovered.getList().contains("4"));
        assertTrue(recovered.getList().contains("5"));

     }

}

