package org.simpleframework.xml.transform;

import java.math.BigInteger;

import org.simpleframework.xml.transform.BigIntegerTransform;

import junit.framework.TestCase;

public class BigIntegerTransformTest extends TestCase {
   
   public void testBigInteger() throws Exception {
      BigInteger integer = new BigInteger("1");
      BigIntegerTransform format = new BigIntegerTransform();
      String value = format.write(integer);
      BigInteger copy = format.read(value);
      
      assertEquals(integer, copy);      
   }
}