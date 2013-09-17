package org.simpleframework.xml.core;

public class Base64Encoder {
   
   private static final int[] REFERENCE = {
    0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  
    0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  
    0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0, 62,  0,  0,  0, 63, 
   52, 53, 54, 55, 56, 57, 58, 59, 60, 61,  0,  0,  0,  0,  0,  0,  
    0,  0,  1,  2,  3,  4,  5,  6,  7,  8,  9, 10, 11, 12, 13, 14, 
   15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25,  0,  0,  0,  0,  0,  
   0,  26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 39, 40, 
   41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51,  0,  0,  0,  0,  0,};
   
   private static final char[] ALPHABET = {
   'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 
   'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z', 
   'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 
   'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', 
   '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/', };

   public static char[] encode(byte[] buf) {
      return encode(buf, 0, buf.length);
   }

   public static char[] encode(byte[] buf, int off, int len) {
      char[] text = new char[((len + 2) / 3) * 4];
      int last = off + len;
      int a = 0;
      int i = 0;

      while (i < last) {
         byte one = buf[i++];
         byte two = (i < len) ? buf[i++] : 0;
         byte three = (i < len) ? buf[i++] : 0;

         int mask = 0x3F;
         text[a++] = ALPHABET[(one >> 2) & mask];
         text[a++] = ALPHABET[((one << 4) | ((two & 0xFF) >> 4)) & mask];
         text[a++] = ALPHABET[((two << 2) | ((three & 0xFF) >> 6)) & mask];
         text[a++] = ALPHABET[three & mask];
      }
      switch (len % 3) {
      case 1:
         text[--a] = '=';
      case 2:
         text[--a] = '=';
      }
      return text;
   }

   public static byte[] decode(char[] text) {
      return decode(text, 0, text.length);
   }
   
   public static byte[] decode(char[] text, int off, int len) {
      int delta = 0;
      
      if(text[off + len -1] == '=') {
         delta = text[off +len -2] == '=' ? 2 : 1;
      }
      byte[] buf = new byte[len * 3 / 4 - delta];
      int mask = 0xff;
      int index = 0;

      for (int i = 0; i < len; i += 4) {
         int pos = off + i;
         int one = REFERENCE[text[pos]];
         int two = REFERENCE[text[pos + 1]];
         
         buf[index++] = (byte) (((one << 2) | (two >> 4)) & mask);
         
         if (index >= buf.length) {
            return buf;
         }
         int three = REFERENCE[text[pos + 2]];
         
         buf[index++] = (byte) (((two << 4) | (three >> 2)) & mask);
         
         if (index >= buf.length) {
            return buf;
         }
         int four = REFERENCE[text[pos + 3]];
         buf[index++] = (byte) (((three << 6) | four) & mask);
      }
      return buf;
   }

}