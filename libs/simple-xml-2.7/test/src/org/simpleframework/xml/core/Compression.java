package org.simpleframework.xml.core;

import java.util.zip.Deflater;

public enum Compression {
   NONE(1, Deflater.NO_COMPRESSION),
   DEFAULT(2, Deflater.DEFAULT_COMPRESSION),
   FASTEST(3, Deflater.BEST_SPEED),
   BEST(4, Deflater.BEST_COMPRESSION);
   
   public final int level;
   public final int code;
   
   private Compression(int code, int level) {
      this.level = level;
      this.code = code;
   }
   
   public boolean isCompression() {
      return this != NONE;
   }
   
   public int getCode() {
      return ordinal();
   }
   
   public int getLevel() {
      return level;
   }
   
   public static Compression resolveCompression(int code) {
      for(Compression compression : values()) {
         if(compression.code == code) {
            return compression;
         }
      }
      return NONE;
   }
}
