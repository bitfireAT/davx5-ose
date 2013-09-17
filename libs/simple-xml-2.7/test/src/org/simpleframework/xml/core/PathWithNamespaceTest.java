package org.simpleframework.xml.core;

import java.io.StringWriter;

import org.simpleframework.xml.Default;
import org.simpleframework.xml.Namespace;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.ValidationTestCase;

public class PathWithNamespaceTest extends ValidationTestCase {
   
   @Default
   @Namespace(reference="http://www.domain.com/root")
   public static class PathWithNamespaceExample {
      @Path("a[1]/b/c")
      @Namespace(reference="http://www.domain.com/name")
      private String name;
      @Path("a[1]/b/c")
      @Namespace(reference="http://www.domain.com/value")
      private String value;
      public void setName(String name) {
         this.name = name;
      }
      public void setValue(String value) {
         this.value = value;
      }
   }
   
   public void testPathWithNamespace() throws Exception {
      Persister persister = new Persister();
      PathWithNamespaceExample example = new PathWithNamespaceExample();
      StringWriter writer = new StringWriter();
      example.setName("Tim");
      example.setValue("Value");
      persister.write(example, writer);
      PathWithNamespaceExample recovered = persister.read(PathWithNamespaceExample.class, writer.toString());
      assertEquals(recovered.name, example.name);
      assertEquals(recovered.value, example.value);
      validate(example, persister);
   }

}
