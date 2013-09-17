package org.simpleframework.xml.core;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public class CompressionOutputStream extends OutputStream {

   private DeflaterOutputStream compressor;
   private Deflater deflater;
   private OutputStream output;
   private Compression compression;
   private byte[] temp;
   private int count;
   
   public CompressionOutputStream(OutputStream output, Compression compression) {
      this.deflater = new Deflater(compression.level);
      this.compressor = new DeflaterOutputStream(output, deflater);
      this.temp = new byte[1];
      this.compression = compression;
      this.output = output;
   }
   
   @Override
   public void write(int octet) throws IOException {
      temp[0] = (byte)octet;
      write(temp);
   }

   @Override
   public void write(byte[] array, int off, int length) throws IOException {
      if(length > 0) {
         if(count == 0) {
            output.write(compression.code);
         }
         if(compression.isCompression()) {
            compressor.write(array, off, length);
         } else {
            output.write(array, off, length);
         }
         count += length;
      }
   }
   
   @Override
   public void flush() throws IOException {
      if(compression.isCompression()) {
         compressor.flush();
      } else {
         output.flush();
      }
   }
   
   @Override
   public void close() throws IOException {
      if(compression.isCompression()) {
         compressor.close();
      } else {
         output.close();
      }
   }
}
