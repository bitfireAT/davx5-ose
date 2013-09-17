package org.simpleframework.xml.core;

import java.io.IOException;
import java.io.InputStream;

public class Base64InputStream extends InputStream {
   
   private char[] encoded;
   private byte[] decoded;
   private byte[] temp;
   private int count;
   
   public Base64InputStream(String source) {
      this.encoded = source.toCharArray();
      this.temp = new byte[1];
   }

   @Override
   public int read() throws IOException {
      int count = read(temp);
      
      if(count == -1) {
         return -1;
      }
      return temp[0] & 0xff;
   }
   
   @Override
   public int read(byte[] array, int off, int len) throws IOException {
      if(decoded == null) {
         decoded = Base64Encoder.decode(encoded);
      }
      if(count >= decoded.length) {
         return -1;
      }
      int size = Math.min(len,  decoded.length - count);
      
      if(size > 0) {
         System.arraycopy(decoded, count, array, off, size);
         count += size;
      }
      return size;
    }
   
   @Override
   public String toString() {
      return new String(decoded);
   }
}
