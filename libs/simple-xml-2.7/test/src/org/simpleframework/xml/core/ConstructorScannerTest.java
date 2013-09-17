package org.simpleframework.xml.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementUnion;
import org.simpleframework.xml.Path;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.stream.Format;

public class ConstructorScannerTest extends SignatureScannerTest {

   public static class ExampleWithTextAndElement {    
      public ExampleWithTextAndElement(
            @Element(name="a") String a, 
            @Element(name="b") String b) {}     
      public ExampleWithTextAndElement(
            @Path("a") @Text String a, 
            @Element(name="b") String b, 
            @Element(name="c") String c) {}  
   }
   
   public static class ClashBetweenElementAndText {    
      public ClashBetweenElementAndText(
            @Path("a/b") @Element(name="c") String a) {}     
      public ClashBetweenElementAndText(
            @Path("a/b/c") @Text String a,
            @Element(name="c") String c) {}  
   }
   
   public static class ClashBetweenElementUnionAndText {
      public ClashBetweenElementUnionAndText(
            @Path("a/b") @ElementUnion({
               @Element(name="x"),
               @Element(name="y")
            }) String a,
            @Path("a/b/x") @Text String b){}
   }
   
   public static class ClashInText {
      public ClashInText(
            @Path("a/b/x") @Text String a,
            @Path("a/b/x") @Text String b){}
   }
   
   
   public static class SameNameWithPath {
      public SameNameWithPath(
            @Path("path[1]") @Attribute(name="a") String x,
            @Path("path[1]") @Element(name="a") String y) {}
   }
   
   public static class SameNameAndAnnotationWithPath {
      public SameNameAndAnnotationWithPath(
            @Path("path[1]") @Element(name="a") String x,
            @Path("path[1]") @Element(name="a") String y) {}
   }
   
   public static class SameNameWithDifferentPath{
      public SameNameWithDifferentPath(
            @Path("path[1]") @Element(name="a") String x,
            @Path("path[2]") @Element(name="a") String y) {}
   }
   
   public void testElementWithPath() throws Exception {
      ConstructorScanner scanner = new ConstructorScanner(new DetailScanner(ExampleWithTextAndElement.class), new Support());
      ParameterMap signature = scanner.getParameters();
      List<Parameter> parameters = signature.getAll();
      Set<String> names = new HashSet<String>();
      
      for(Parameter parameter : parameters) {
         String path = parameter.getPath();
         names.add(path);
      }
      assertTrue(names.contains("a"));
      assertTrue(names.contains("a[1]"));
      assertTrue(names.contains("b"));
      assertTrue(names.contains("c"));     
   }
   
   public void testClashOnElementByPath() throws Exception {
      boolean failure = false;
      
      try {
         new ConstructorScanner(new DetailScanner(ClashBetweenElementAndText.class), new Support());
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Failure should occur because there is a clash in element and text", failure);
   }
  
   public void testClashOnElementUnionByPath() throws Exception {
      boolean failure = false;
      
      try {
         new ConstructorScanner(new DetailScanner(ClashBetweenElementUnionAndText.class), new Support());
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Failure should occur because there is a clash in element and text", failure);
   }
   
   public void testClashInText() throws Exception {
      boolean failure = false;
      
      try {
         new ConstructorScanner(new DetailScanner(ClashInText.class), new Support());
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Failure should occur when there is a clash in text", failure);
   }
   
   public void testSameNameWithPath() throws Exception {
      ConstructorScanner scanner = new ConstructorScanner(new DetailScanner(SameNameWithPath.class), new Support());
      ParameterMap signature = scanner.getParameters();
      List<Parameter> parameters = signature.getAll();
      Set<String> names = new HashSet<String>();
      
      for(Parameter parameter : parameters) {
         String path = parameter.getPath();
         names.add(path);
      }
      assertTrue(names.contains("path[1]/@a"));
      assertTrue(names.contains("path[1]/a[1]"));
   }
   
   public void testSameNameAndAnnotationWithPath() throws Exception {
      boolean failure = false;
      
      try {
         new ConstructorScanner(new DetailScanner(SameNameAndAnnotationWithPath.class), new Support());
      }catch(Exception e) {
         e.printStackTrace();
         failure = true;
      }
      assertTrue("Failure should occur when there is an ambiguous parameter", failure);
   }
   
   public void testSameNameWithDifferentPath() throws Exception {
      ConstructorScanner scanner = new ConstructorScanner(new DetailScanner(SameNameWithDifferentPath.class), new Support());
      ParameterMap signature = scanner.getParameters();
      List<Parameter> parameters = signature.getAll();
      Set<String> names = new HashSet<String>();
      
      for(Parameter parameter : parameters) {
         String path = parameter.getPath();
         names.add(path);
      }
      assertTrue(names.contains("path[1]/a[1]"));
      assertTrue(names.contains("path[2]/a[1]"));
   }
}
