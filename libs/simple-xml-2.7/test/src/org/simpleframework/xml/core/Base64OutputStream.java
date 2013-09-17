package org.simpleframework.xml.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

public class Base64OutputStream extends OutputStream {
   
   private char[] encoded;
   private byte[] buffer;
   private byte[] temp;
   private int count;
   
   public Base64OutputStream() {
      this(1024);
   }
   
   public Base64OutputStream(int capacity) {
      this.buffer = new byte[capacity];
      this.temp = new byte[1];
   }
   
   @Override
   public void write(int octet) throws IOException {
      temp[0] = (byte)octet;
      write(temp);
   }

   @Override
   public void write(byte[] array, int off, int length) throws IOException {
      if(encoded != null) {
         throw new IOException("Stream has been closed");
      }
      if(count + length > buffer.length) {
         expand(count + length);
      }
      System.arraycopy(array, off, buffer, count, length);
      count += length;
   }
   
   private void expand(int capacity) throws IOException {
      int length = Math.max(buffer.length * 2, capacity);
      
      if(buffer.length < capacity) {
         buffer = Arrays.copyOf(buffer, length);
      }
   }
   
   @Override
   public void close() throws IOException {
      if(encoded == null) {
         encoded = Base64Encoder.encode(buffer, 0, count);
      }
   }
   
   @Override
   public String toString() {
      return new String(encoded);
   }
}
