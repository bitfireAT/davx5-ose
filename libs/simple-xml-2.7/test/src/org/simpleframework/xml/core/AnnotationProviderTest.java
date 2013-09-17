package org.simpleframework.xml.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import junit.framework.TestCase;

import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementMap;

public class AnnotationProviderTest extends TestCase {
   
   public void testProvider() throws Exception {
      assertTrue(ElementMap.class.isAssignableFrom(new AnnotationFactory(new DetailScanner(AnnotationProviderTest.class), new Support()).getInstance(Map.class, null).getClass()));
      assertTrue(ElementMap.class.isAssignableFrom(new AnnotationFactory(new DetailScanner(AnnotationProviderTest.class), new Support()).getInstance(HashMap.class, null).getClass()));
      assertTrue(ElementMap.class.isAssignableFrom(new AnnotationFactory(new DetailScanner(AnnotationProviderTest.class), new Support()).getInstance(ConcurrentHashMap.class, null).getClass()));
      assertTrue(ElementMap.class.isAssignableFrom(new AnnotationFactory(new DetailScanner(AnnotationProviderTest.class), new Support()).getInstance(LinkedHashMap.class, null).getClass()));
      assertTrue(ElementMap.class.isAssignableFrom(new AnnotationFactory(new DetailScanner(AnnotationProviderTest.class), new Support()).getInstance(Map.class, null).getClass()));
      assertTrue(ElementList.class.isAssignableFrom(new AnnotationFactory(new DetailScanner(AnnotationProviderTest.class), new Support()).getInstance(Set.class, null).getClass()));
      assertTrue(ElementList.class.isAssignableFrom(new AnnotationFactory(new DetailScanner(AnnotationProviderTest.class), new Support()).getInstance(Collection.class, null).getClass()));
      assertTrue(ElementList.class.isAssignableFrom(new AnnotationFactory(new DetailScanner(AnnotationProviderTest.class), new Support()).getInstance(List.class, null).getClass()));
      assertTrue(ElementList.class.isAssignableFrom(new AnnotationFactory(new DetailScanner(AnnotationProviderTest.class), new Support()).getInstance(TreeSet.class, null).getClass()));
      assertTrue(ElementList.class.isAssignableFrom(new AnnotationFactory(new DetailScanner(AnnotationProviderTest.class), new Support()).getInstance(HashSet.class, null).getClass()));
      assertTrue(ElementList.class.isAssignableFrom(new AnnotationFactory(new DetailScanner(AnnotationProviderTest.class), new Support()).getInstance(ArrayList.class, null).getClass()));
   }

}
