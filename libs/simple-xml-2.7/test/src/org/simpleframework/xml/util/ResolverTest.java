package org.simpleframework.xml.util;

import java.util.Iterator;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.core.Persister;

public class ResolverTest extends ValidationTestCase {
        
   private static final String LIST = 
   "<?xml version=\"1.0\"?>\n"+
   "<test name='example'>\n"+
   "   <list>\n"+  
   "      <match pattern='*.html' value='text/html'/>\n"+
   "      <match pattern='*.jpg' value='image/jpeg'/>\n"+
   "      <match pattern='/images/*' value='image/jpeg'/>\n"+
   "      <match pattern='/log/**' value='text/plain'/>\n"+
   "      <match pattern='*.exe' value='application/octetstream'/>\n"+
   "      <match pattern='**.txt' value='text/plain'/>\n"+
   "      <match pattern='/html/*' value='text/html'/>\n"+
   "   </list>\n"+
   "</test>";  
   
   @Root(name="match")
   private static class ContentType implements Match {

      @Attribute(name="value")
      private String value;   
      
      @Attribute
      private String pattern;

      public ContentType() {
         super();                  
      }

      public ContentType(String pattern, String value) {
         this.pattern = pattern;
         this.value = value;        
      }
      
      public String getPattern() {
         return pattern;
      }
      
      public String toString() {
         return String.format("%s=%s", pattern, value);
      }
   }
   
   @Root(name="test")
   private static class ContentResolver implements Iterable<ContentType> {

      @ElementList(name="list", type=ContentType.class)
      private Resolver<ContentType> list;           

      @Attribute(name="name")
      private String name;

      private ContentResolver() {
         this.list = new Resolver<ContentType>();              
      }

      public Iterator<ContentType> iterator() {
         return list.iterator();
      }

      public void add(ContentType type) {
         list.add(type);              
      }

      public ContentType resolve(String name) {
         return list.resolve(name);              
      }

      public int size() {
         return list.size();              
      }
   }
        
	private Persister serializer;

	public void setUp() {
	   serializer = new Persister();
	}
	
   public void testResolver() throws Exception {    
      ContentResolver resolver = (ContentResolver) serializer.read(ContentResolver.class, LIST);

      assertEquals(7, resolver.size());
      assertEquals("image/jpeg", resolver.resolve("image.jpg").value);
      assertEquals("text/plain", resolver.resolve("README.txt").value);
      assertEquals("text/html", resolver.resolve("/index.html").value);
      assertEquals("text/html", resolver.resolve("/html/image.jpg").value);
      assertEquals("text/plain", resolver.resolve("/images/README.txt").value);
      assertEquals("text/plain", resolver.resolve("/log/access.log").value);
      
      validate(resolver, serializer);
   }
   
   public void testCache() throws Exception {    
      ContentResolver resolver = (ContentResolver) serializer.read(ContentResolver.class, LIST);

      assertEquals(7, resolver.size());
      assertEquals("image/jpeg", resolver.resolve("image.jpg").value);
      assertEquals("text/plain", resolver.resolve("README.txt").value);
      
      Iterator<ContentType> it = resolver.iterator();
      
      while(it.hasNext()) {
         ContentType type = it.next();
         
         if(type.value.equals("text/plain")) {
            it.remove();
            break;
         }
      }
      resolver.add(new ContentType("*", "application/octetstream"));

      assertEquals("application/octetstream", resolver.resolve("README.txt").value);
      assertEquals("application/octetstream", resolver.resolve("README.txt").value);            
      
      resolver.add(new ContentType("README.*", "text/html"));
      resolver.add(new ContentType("README.txt", "text/plain"));

      assertEquals("text/plain", resolver.resolve("README.txt").value);
      assertEquals("text/html", resolver.resolve("README.jsp").value);

      validate(resolver, serializer);
   }

   public void testNoResolution() throws Exception {
      ContentResolver resolver = (ContentResolver) serializer.read(ContentResolver.class, LIST);

      assertEquals(7, resolver.size());
      assertEquals("text/plain", resolver.resolve("README.txt").value);
      assertEquals(null, resolver.resolve("README"));           
   }

   public void testNonGreedyMatch() throws Exception {
      ContentResolver resolver = (ContentResolver) serializer.read(ContentResolver.class, LIST);

      assertEquals(7, resolver.size());
      resolver.add(new ContentType("/*?/html/*", "text/html"));
      assertEquals(8, resolver.size());
      assertEquals(null, resolver.resolve("/a/b/html/index.jsp"));
      assertEquals("text/html", resolver.resolve("/a/html/index.jsp").value);
   }

   public void testResolverCache() throws Exception {
      ContentResolver resolver = new ContentResolver();           

      for(int i = 0; i <= 2000; i++) {
         resolver.add(new ContentType(String.valueOf(i), String.valueOf(i)));          
      }
      assertEquals(resolver.resolve("1").value, "1");
      assertEquals(resolver.resolve("2000").value, "2000");
   }
}
