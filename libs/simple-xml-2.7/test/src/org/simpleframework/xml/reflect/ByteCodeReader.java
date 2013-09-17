package org.simpleframework.xml.reflect;

import java.io.IOException;
import java.io.InputStream;

/***
 * Portions Copyright (c) 2007 Paul Hammant Portions copyright (c)
 * 2000-2007 INRIA, France Telecom All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met: 1. Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer. 2.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution. 3.
 * Neither the name of the copyright holders nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

/**
 * A Java class parser to make a Class Visitor visit an existing class.
 * This class parses a byte array conforming to the Java class file format
 * and calls the appropriate visit methods of a given class visitor for
 * each field, method and bytecode instruction encountered.
 * 
 * @author Eric Bruneton
 * @author Eugene Kuleshov
 */
class ClassReader {

   /**
    * The class to be parsed. <i>The content of this array must not be
    * modified. This field is intended for Attribute sub classes, and is
    * normally not needed by class generators or adapters.</i>
    */
   public final byte[] b;

   /**
    * The start index of each constant pool item in {@link #b b}, plus
    * one. The one byte offset skips the constant pool item tag that
    * indicates its type.
    */
   private final int[] items;

   /**
    * The String objects corresponding to the CONSTANT_Utf8 items. This
    * cache avoids multiple parsing of a given CONSTANT_Utf8 constant pool
    * item, which GREATLY improves performances (by a factor 2 to 3). This
    * caching strategy could be extended to all constant pool items, but
    * its benefit would not be so great for these items (because they are
    * much less expensive to parse than CONSTANT_Utf8 items).
    */
   private final String[] strings;

   /**
    * Maximum length of the strings contained in the constant pool of the
    * class.
    */
   private final int maxStringLength;

   /**
    * Start index of the class header information (access, name...) in
    * {@link #b b}.
    */
   public final int header;

   /**
    * The type of CONSTANT_Fieldref constant pool items.
    */
   final static int FIELD = 9;

   /**
    * The type of CONSTANT_Methodref constant pool items.
    */
   final static int METH = 10;

   /**
    * The type of CONSTANT_InterfaceMethodref constant pool items.
    */
   final static int IMETH = 11;

   /**
    * The type of CONSTANT_Integer constant pool items.
    */
   final static int INT = 3;

   /**
    * The type of CONSTANT_Float constant pool items.
    */
   final static int FLOAT = 4;

   /**
    * The type of CONSTANT_Long constant pool items.
    */
   final static int LONG = 5;

   /**
    * The type of CONSTANT_Double constant pool items.
    */
   final static int DOUBLE = 6;

   /**
    * The type of CONSTANT_NameAndType constant pool items.
    */
   final static int NAME_TYPE = 12;

   /**
    * The type of CONSTANT_Utf8 constant pool items.
    */
   final static int UTF8 = 1;

   // ------------------------------------------------------------------------
   // Constructors
   // ------------------------------------------------------------------------

   /**
    * Constructs a new {@link ClassReader} object.
    * 
    * @param b
    *           the bytecode of the class to be read.
    */
   private ClassReader(final byte[] b) {
      this(b, 0);
   }

   /**
    * Constructs a new {@link ClassReader} object.
    * 
    * @param b
    *           the bytecode of the class to be read.
    * @param off
    *           the start offset of the class data.
    */
   private ClassReader(final byte[] b, final int off) {
      this.b = b;
      // parses the constant pool
      items = new int[readUnsignedShort(off + 8)];
      int n = items.length;
      strings = new String[n];
      int max = 0;
      int index = off + 10;
      for (int i = 1; i < n; ++i) {
         items[i] = index + 1;
         int size;
         switch (b[index]) {
         case FIELD:
         case METH:
         case IMETH:
         case INT:
         case FLOAT:
         case NAME_TYPE:
            size = 5;
            break;
         case LONG:
         case DOUBLE:
            size = 9;
            ++i;
            break;
         case UTF8:
            size = 3 + readUnsignedShort(index + 1);
            if (size > max) {
               max = size;
            }
            break;
         // case HamConstants.CLASS:
         // case HamConstants.STR:
         default:
            size = 3;
            break;
         }
         index += size;
      }
      maxStringLength = max;
      // the class header information starts just after the constant pool
      header = index;
   }

   /**
    * Constructs a new {@link ClassReader} object.
    * 
    * @param is
    *           an input stream from which to read the class.
    * @throws IOException
    *            if a problem occurs during reading.
    */
   public ClassReader(final InputStream is) throws IOException {
      this(readClass(is));
   }

   /**
    * Reads the bytecode of a class.
    * 
    * @param is
    *           an input stream from which to read the class.
    * @return the bytecode read from the given input stream.
    * @throws IOException
    *            if a problem occurs during reading.
    */
   private static byte[] readClass(final InputStream is)
         throws IOException {
      if (is == null) {
         throw new IOException("Class not found");
      }
      byte[] b = new byte[is.available()];
      int len = 0;
      while (true) {
         int n = is.read(b, len, b.length - len);
         if (n == -1) {
            if (len < b.length) {
               byte[] c = new byte[len];
               System.arraycopy(b, 0, c, 0, len);
               b = c;
            }
            return b;
         }
         len += n;
         if (len == b.length) {
            int last = is.read();
            if (last < 0) {
               return b;
            }
            byte[] c = new byte[b.length + 1000];
            System.arraycopy(b, 0, c, 0, len);
            c[len++] = (byte) last;
            b = c;
         }
      }
   }

   // ------------------------------------------------------------------------
   // Public methods
   // ------------------------------------------------------------------------

   /**
    * Makes the given visitor visit the Java class of this
    * {@link ClassReader}. This class is the one specified in the
    * constructor (see {@link #ClassReader(byte[]) ClassReader}).
    * 
    * @param classVisitor
    *           the visitor that must visit this class.
    */
   public void accept(final TypeCollector classVisitor) {
      char[] c = new char[maxStringLength]; // buffer used to read strings
      int i, j, k; // loop variables
      int u, v, w; // indexes in b

      String attrName;
      int anns = 0;
      int ianns = 0;

      // visits the header
      u = header;
      v = items[readUnsignedShort(u + 4)];
      int len = readUnsignedShort(u + 6);
      w = 0;
      u += 8;
      for (i = 0; i < len; ++i) {
         u += 2;
      }
      v = u;
      i = readUnsignedShort(v);
      v += 2;
      for (; i > 0; --i) {
         j = readUnsignedShort(v + 6);
         v += 8;
         for (; j > 0; --j) {
            v += 6 + readInt(v + 2);
         }
      }
      i = readUnsignedShort(v);
      v += 2;
      for (; i > 0; --i) {
         j = readUnsignedShort(v + 6);
         v += 8;
         for (; j > 0; --j) {
            v += 6 + readInt(v + 2);
         }
      }

      i = readUnsignedShort(v);
      v += 2;
      for (; i > 0; --i) {
         v += 6 + readInt(v + 2);
      }

      // annotations not needed.

      // visits the fields
      i = readUnsignedShort(u);
      u += 2;
      for (; i > 0; --i) {
         j = readUnsignedShort(u + 6);
         u += 8;
         for (; j > 0; --j) {
            u += 6 + readInt(u + 2);
         }
      }

      // visits the methods
      i = readUnsignedShort(u);
      u += 2;
      for (; i > 0; --i) {
         // inlined in original ASM source, now a method call
         u = readMethod(classVisitor, c, u);
      }

   }

   private int readMethod(TypeCollector classVisitor, char[] c, int u) {
      int v;
      int w;
      int j;
      String attrName;
      int k;
      int access = readUnsignedShort(u);
      String name = readUTF8(u + 2, c);
      String desc = readUTF8(u + 4, c);
      v = 0;
      w = 0;

      // looks for Code and Exceptions attributes
      j = readUnsignedShort(u + 6);
      u += 8;
      for (; j > 0; --j) {
         attrName = readUTF8(u, c);
         int attrSize = readInt(u + 2);
         u += 6;
         // tests are sorted in decreasing frequency order
         // (based on frequencies observed on typical classes)
         if (attrName.equals("Code")) {
            v = u;
         }
         u += attrSize;
      }
      // reads declared exceptions
      if (w == 0) {
      } else {
         w += 2;
         for (j = 0; j < readUnsignedShort(w); ++j) {
            w += 2;
         }
      }

      // visits the method's code, if any
      MethodCollector mv = classVisitor.visitMethod(access, name, desc);

      if (mv != null && v != 0) {
         int codeLength = readInt(v + 4);
         v += 8;

         int codeStart = v;
         int codeEnd = v + codeLength;
         v = codeEnd;

         j = readUnsignedShort(v);
         v += 2;
         for (; j > 0; --j) {
            v += 8;
         }
         // parses the local variable, line number tables, and code
         // attributes
         int varTable = 0;
         int varTypeTable = 0;
         j = readUnsignedShort(v);
         v += 2;
         for (; j > 0; --j) {
            attrName = readUTF8(v, c);
            if (attrName.equals("LocalVariableTable")) {
               varTable = v + 6;
            } else if (attrName.equals("LocalVariableTypeTable")) {
               varTypeTable = v + 6;
            }
            v += 6 + readInt(v + 2);
         }

         v = codeStart;
         // visits the local variable tables
         if (varTable != 0) {
            if (varTypeTable != 0) {
               k = readUnsignedShort(varTypeTable) * 3;
               w = varTypeTable + 2;
               int[] typeTable = new int[k];
               while (k > 0) {
                  typeTable[--k] = w + 6; // signature
                  typeTable[--k] = readUnsignedShort(w + 8); // index
                  typeTable[--k] = readUnsignedShort(w); // start
                  w += 10;
               }
            }
            k = readUnsignedShort(varTable);
            w = varTable + 2;
            for (; k > 0; --k) {
               int index = readUnsignedShort(w + 8);
               mv.visitLocalVariable(readUTF8(w + 4, c), index);
               w += 10;
            }
         }
      }
      return u;
   }

   /**
    * Reads an unsigned short value in {@link #b b}. <i>This method is
    * intended for Attribute sub classes, and is normally not needed by
    * class generators or adapters.</i>
    * 
    * @param index
    *           the start index of the value to be read in {@link #b b}.
    * @return the read value.
    */
   private int readUnsignedShort(final int index) {
      byte[] b = this.b;
      return ((b[index] & 0xFF) << 8) | (b[index + 1] & 0xFF);
   }

   /**
    * Reads a signed int value in {@link #b b}. <i>This method is intended
    * for Attribute sub classes, and is normally not needed by class
    * generators or adapters.</i>
    * 
    * @param index
    *           the start index of the value to be read in {@link #b b}.
    * @return the read value.
    */
   private int readInt(final int index) {
      byte[] b = this.b;
      return ((b[index] & 0xFF) << 24) | ((b[index + 1] & 0xFF) << 16)
            | ((b[index + 2] & 0xFF) << 8) | (b[index + 3] & 0xFF);
   }

   /**
    * Reads an UTF8 string constant pool item in {@link #b b}. <i>This
    * method is intended for Attribute sub classes, and is normally not
    * needed by class generators or adapters.</i>
    * 
    * @param index
    *           the start index of an unsigned short value in {@link #b b}
    *           , whose value is the index of an UTF8 constant pool item.
    * @param buf
    *           buffer to be used to read the item. This buffer must be
    *           sufficiently large. It is not automatically resized.
    * @return the String corresponding to the specified UTF8 item.
    */
   private String readUTF8(int index, final char[] buf) {
      int item = readUnsignedShort(index);
      String s = strings[item];
      if (s != null) {
         return s;
      }
      index = items[item];
      return strings[item] = readUTF(index + 2, readUnsignedShort(index),
            buf);
   }

   /**
    * Reads UTF8 string in {@link #b b}.
    * 
    * @param index
    *           start offset of the UTF8 string to be read.
    * @param utfLen
    *           length of the UTF8 string to be read.
    * @param buf
    *           buffer to be used to read the string. This buffer must be
    *           sufficiently large. It is not automatically resized.
    * @return the String corresponding to the specified UTF8 string.
    */
   private String readUTF(int index, final int utfLen, final char[] buf) {
      int endIndex = index + utfLen;
      byte[] b = this.b;
      int strLen = 0;
      int c;
      int st = 0;
      char cc = 0;
      while (index < endIndex) {
         c = b[index++];
         switch (st) {
         case 0:
            c = c & 0xFF;
            if (c < 0x80) { // 0xxxxxxx
               buf[strLen++] = (char) c;
            } else if (c < 0xE0 && c > 0xBF) { // 110x xxxx 10xx xxxx
               cc = (char) (c & 0x1F);
               st = 1;
            } else { // 1110 xxxx 10xx xxxx 10xx xxxx
               cc = (char) (c & 0x0F);
               st = 2;
            }
            break;

         case 1: // byte 2 of 2-byte char or byte 3 of 3-byte char
            buf[strLen++] = (char) ((cc << 6) | (c & 0x3F));
            st = 0;
            break;

         case 2: // byte 2 of 3-byte char
            cc = (char) ((cc << 6) | (c & 0x3F));
            st = 1;
            break;
         }
      }
      return new String(buf, 0, strLen);
   }

}