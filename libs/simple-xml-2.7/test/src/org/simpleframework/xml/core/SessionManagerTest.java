package org.simpleframework.xml.core;

import junit.framework.TestCase;

public class SessionManagerTest extends TestCase {
   
   public void testManager() throws Exception{
      SessionManager manager = new SessionManager();
      Session session1 = manager.open(true);
      Session session2 = manager.open(false);
      
      assertEquals(session1.isStrict(), session2.isStrict());
      assertEquals(session1.isEmpty(), session2.isEmpty());
      
      session1.put("a", "A");
      session1.put("b", "B");
      
      assertEquals(session2.get("a"), "A");
      assertEquals(session2.get("b"), "B");
      assertEquals(session1, session2);
      
      Session session3 = manager.open();
      
      assertEquals(session3.get("a"), "A");
      assertEquals(session3.get("b"), "B");
      assertEquals(session1, session3);
      
      manager.close();
      manager.close();
      
      Session session4 = manager.open();
      
      assertEquals(session1.isStrict(), session4.isStrict());
      assertEquals(session1.isEmpty(), session4.isEmpty());
      assertEquals(session4.get("a"), "A");
      assertEquals(session4.get("b"), "B");
      assertEquals(session1, session4);
      
      manager.close();
      manager.close();
      
      Session session5 = manager.open(false);
      
      assertTrue(session5.isEmpty());
      assertTrue(session1 != session5);
      assertTrue(!session1.equals(session5));
   }

}
