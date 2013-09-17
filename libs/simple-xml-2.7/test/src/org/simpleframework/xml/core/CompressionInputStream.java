package org.simpleframework.xml.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class CompressionInputStream extends InputStream {

   private InflaterInputStream decompressor;
   private Inflater inflater;
   private InputStream source;
   private Compression compression;
   private byte[] temp;
   
   public CompressionInputStream(InputStream source) {
      this.inflater = new Inflater();
      this.decompressor = new InflaterInputStream(source, inflater);
      this.temp = new byte[1];
      this.source = source;
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
      if(compression == null) {
         int code = source.read();
         
         if(code == -1) {
            return -1;
         }
         compression = Compression.resolveCompression(code);
      }
      if(compression.isCompression()) {
         return decompressor.read(array, off, len);
      }
      return source.read(array, off, len);
   }
   
   @Override
   public void close() throws IOException {
      if(compression != null) {
         if(compression.isCompression()) {
            decompressor.close();
         } else {
            source.close();
         }
      } else {
         source.close();
      }
   }

}
