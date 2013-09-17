package org.simpleframework.xml.core;

import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

public class CompressionMarshaller implements Marshaller {

   private final Compression compression;
   private final int buffer;
   
   public CompressionMarshaller() {
      this(Compression.NONE);
   }
   
   public CompressionMarshaller(Compression compression) {
      this(compression, 2048);
   }
   
   public CompressionMarshaller(Compression compression, int buffer) {
      this.compression = compression;
      this.buffer = buffer;
   }
   
   public String marshallText(Object value, Class type) throws Exception {
      OutputStream encoder = new Base64OutputStream(buffer);
      OutputStream compressor = new CompressionOutputStream(encoder, compression);
      ObjectOutput serializer = new ObjectOutputStream(compressor);

      serializer.writeObject(value);
      serializer.close();
      
      return encoder.toString();  
   }

   public Object unmarshallText(String value, Class type) throws Exception {
      InputStream decoder = new Base64InputStream(value);
      InputStream decompressor = new CompressionInputStream(decoder);
      ObjectInput deserializer = new ObjectInputStream(decompressor);

      return deserializer.readObject();
   }
}
