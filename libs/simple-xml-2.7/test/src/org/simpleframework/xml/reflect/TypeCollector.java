package org.simpleframework.xml.reflect;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * The type collector waits for an specific method in order to start a
 * method collector.
 * 
 * @author Guilherme Silveira
 */
class TypeCollector {
   static final String[] EMPTY_NAMES = new String[0];

   private static final Map<String, String> primitives = new HashMap<String, String>() {
      {
         put("int", "I");
         put("boolean", "Z");
         put("char", "C");
         put("short", "B");
         put("float", "F");
         put("long", "J");
         put("double", "D");
      }
   };

   private static final String COMMA = ",";

   private final String methodName;

   private final Class<?>[] parameterTypes;
   private final boolean throwExceptionIfMissing;

   private MethodCollector collector;

   public TypeCollector(String methodName, Class<?>[] parameterTypes,
         boolean throwExceptionIfMissing) {
      this.methodName = methodName;
      this.parameterTypes = parameterTypes;
      this.throwExceptionIfMissing = throwExceptionIfMissing;
      this.collector = null;
   }

   public MethodCollector visitMethod(int access, String name, String desc) {
      // already found the method, skip any processing
      if (collector != null) {
         return null;
      }
      // not the same name
      if (!name.equals(methodName)) {
         return null;
      }
      Type[] argumentTypes = Type.getArgumentTypes(desc);
      int longOrDoubleQuantity = 0;
      for (Type t : argumentTypes) {
         if (t.getClassName().equals("long")
               || t.getClassName().equals("double")) {
            longOrDoubleQuantity++;
         }
      }
      int paramCount = argumentTypes.length;
      // not the same quantity of parameters
      if (paramCount != this.parameterTypes.length) {
         return null;
      }
      for (int i = 0; i < argumentTypes.length; i++) {
         if (!correctTypeName(argumentTypes, i).equals(
               this.parameterTypes[i].getName())) {
            return null;
         }
      }
      this.collector = new MethodCollector((Modifier.isStatic(access) ? 0
            : 1), argumentTypes.length + longOrDoubleQuantity);
      return collector;
   }

   private String correctTypeName(Type[] argumentTypes, int i) {
      String s = argumentTypes[i].getClassName();
      // array notation needs cleanup.
      if (s.endsWith("[]")) {
         String prefix = s.substring(0, s.length() - 2);
         if (primitives.containsKey(prefix)) {
            s = "[" + primitives.get(prefix);
         } else {
            s = "[L" + prefix + ";";
         }
      }
      return s;
   }

   public String[] getParameterNamesForMethod() {
      if (collector == null) {
         return EMPTY_NAMES;
      }
      if (!collector.isDebugInfoPresent()) {
         if (throwExceptionIfMissing) {
            throw new RuntimeException("Parameter names not found for "
                  + methodName);
         } else {
            return EMPTY_NAMES;
         }
      }
      return collector.getResult().split(COMMA);
   }
   
   private static class Type {

      /**
       * The sort of the <tt>void</tt> type.
       */
      private final static int VOID = 0;

      /**
       * The sort of the <tt>boolean</tt> type.
       */
      private final static int BOOLEAN = 1;

      /**
       * The sort of the <tt>char</tt> type.
       */
      private final static int CHAR = 2;

      /**
       * The sort of the <tt>byte</tt> type.
       */
      private final static int BYTE = 3;

      /**
       * The sort of the <tt>short</tt> type.
       */
      private final static int SHORT = 4;

      /**
       * The sort of the <tt>int</tt> type.
       */
      private final static int INT = 5;

      /**
       * The sort of the <tt>float</tt> type.
       */
      private final static int FLOAT = 6;

      /**
       * The sort of the <tt>long</tt> type.
       */
      private final static int LONG = 7;

      /**
       * The sort of the <tt>double</tt> type.
       */
      private final static int DOUBLE = 8;

      /**
       * The sort of array reference types.
       */
      private final static int ARRAY = 9;

      /**
       * The sort of object reference type.
       */
      private final static int OBJECT = 10;

      /**
       * The <tt>void</tt> type.
       */
      private final static Type VOID_TYPE = new Type(VOID, null, ('V' << 24)
            | (5 << 16) | (0 << 8) | 0, 1);

      /**
       * The <tt>boolean</tt> type.
       */
      private final static Type BOOLEAN_TYPE = new Type(BOOLEAN, null,
            ('Z' << 24) | (0 << 16) | (5 << 8) | 1, 1);

      /**
       * The <tt>char</tt> type.
       */
      private final static Type CHAR_TYPE = new Type(CHAR, null, ('C' << 24)
            | (0 << 16) | (6 << 8) | 1, 1);

      /**
       * The <tt>byte</tt> type.
       */
      private final static Type BYTE_TYPE = new Type(BYTE, null, ('B' << 24)
            | (0 << 16) | (5 << 8) | 1, 1);

      /**
       * The <tt>short</tt> type.
       */
      private final static Type SHORT_TYPE = new Type(SHORT, null,
            ('S' << 24) | (0 << 16) | (7 << 8) | 1, 1);

      /**
       * The <tt>int</tt> type.
       */
      private final static Type INT_TYPE = new Type(INT, null, ('I' << 24)
            | (0 << 16) | (0 << 8) | 1, 1);

      /**
       * The <tt>float</tt> type.
       */
      private final static Type FLOAT_TYPE = new Type(FLOAT, null,
            ('F' << 24) | (2 << 16) | (2 << 8) | 1, 1);

      /**
       * The <tt>long</tt> type.
       */
      private final static Type LONG_TYPE = new Type(LONG, null, ('J' << 24)
            | (1 << 16) | (1 << 8) | 2, 1);

      /**
       * The <tt>double</tt> type.
       */
      private final static Type DOUBLE_TYPE = new Type(DOUBLE, null,
            ('D' << 24) | (3 << 16) | (3 << 8) | 2, 1);

      // ------------------------------------------------------------------------
      // Fields
      // ------------------------------------------------------------------------

      /**
       * The sort of this Java type.
       */
      private final int sort;

      /**
       * A buffer containing the internal name of this Java type. This field
       * is only used for reference types.
       */
      private char[] buf;

      /**
       * The offset of the internal name of this Java type in {@link #buf
       * buf} or, for primitive types, the size, descriptor and getOpcode
       * offsets for this type (byte 0 contains the size, byte 1 the
       * descriptor, byte 2 the offset for IALOAD or IASTORE, byte 3 the
       * offset for all other instructions).
       */
      private int off;

      /**
       * The length of the internal name of this Java type.
       */
      private final int len;

      // ------------------------------------------------------------------------
      // Constructors
      // ------------------------------------------------------------------------

      /**
       * Constructs a primitive type.
       * 
       * @param sort
       *           the sort of the primitive type to be constructed.
       */
      private Type(final int sort) {
         this.sort = sort;
         this.len = 1;
      }

      /**
       * Constructs a reference type.
       * 
       * @param sort
       *           the sort of the reference type to be constructed.
       * @param buf
       *           a buffer containing the descriptor of the previous type.
       * @param off
       *           the offset of this descriptor in the previous buffer.
       * @param len
       *           the length of this descriptor.
       */
      private Type(final int sort, final char[] buf, final int off,
            final int len) {
         this.sort = sort;
         this.buf = buf;
         this.off = off;
         this.len = len;
      }

      /**
       * Returns the Java types corresponding to the argument types of the
       * given method descriptor.
       * 
       * @param methodDescriptor
       *           a method descriptor.
       * @return the Java types corresponding to the argument types of the
       *         given method descriptor.
       */
      private static Type[] getArgumentTypes(final String methodDescriptor) {
         char[] buf = methodDescriptor.toCharArray();
         int off = 1;
         int size = 0;
         while (true) {
            char car = buf[off++];
            if (car == ')') {
               break;
            } else if (car == 'L') {
               while (buf[off++] != ';') {
               }
               ++size;
            } else if (car != '[') {
               ++size;
            }
         }

         Type[] args = new Type[size];
         off = 1;
         size = 0;
         while (buf[off] != ')') {
            args[size] = getType(buf, off);
            off += args[size].len + (args[size].sort == OBJECT ? 2 : 0);
            size += 1;
         }
         return args;
      }

      /**
       * Returns the Java type corresponding to the given type descriptor.
       * 
       * @param buf
       *           a buffer containing a type descriptor.
       * @param off
       *           the offset of this descriptor in the previous buffer.
       * @return the Java type corresponding to the given type descriptor.
       */
      private static Type getType(final char[] buf, final int off) {
         int len;
         switch (buf[off]) {
         case 'V':
            return VOID_TYPE;
         case 'Z':
            return BOOLEAN_TYPE;
         case 'C':
            return CHAR_TYPE;
         case 'B':
            return BYTE_TYPE;
         case 'S':
            return SHORT_TYPE;
         case 'I':
            return INT_TYPE;
         case 'F':
            return FLOAT_TYPE;
         case 'J':
            return LONG_TYPE;
         case 'D':
            return DOUBLE_TYPE;
         case '[':
            len = 1;
            while (buf[off + len] == '[') {
               ++len;
            }
            if (buf[off + len] == 'L') {
               ++len;
               while (buf[off + len] != ';') {
                  ++len;
               }
            }
            return new Type(ARRAY, buf, off, len + 1);
            // case 'L':
         default:
            len = 1;
            while (buf[off + len] != ';') {
               ++len;
            }
            return new Type(OBJECT, buf, off + 1, len - 1);
         }
      }

      // ------------------------------------------------------------------------
      // Accessors
      // ------------------------------------------------------------------------

      /**
       * Returns the number of dimensions of this array type. This method
       * should only be used for an array type.
       * 
       * @return the number of dimensions of this array type.
       */
      private int getDimensions() {
         int i = 1;
         while (buf[off + i] == '[') {
            ++i;
         }
         return i;
      }

      /**
       * Returns the type of the elements of this array type. This method
       * should only be used for an array type.
       * 
       * @return Returns the type of the elements of this array type.
       */
      private Type getElementType() {
         return getType(buf, off + getDimensions());
      }

      /**
       * Returns the name of the class corresponding to this type.
       * 
       * @return the fully qualified name of the class corresponding to this
       *         type.
       */
      private String getClassName() {
         switch (sort) {
         case VOID:
            return "void";
         case BOOLEAN:
            return "boolean";
         case CHAR:
            return "char";
         case BYTE:
            return "byte";
         case SHORT:
            return "short";
         case INT:
            return "int";
         case FLOAT:
            return "float";
         case LONG:
            return "long";
         case DOUBLE:
            return "double";
         case ARRAY:
            StringBuffer b = new StringBuffer(getElementType()
                  .getClassName());
            for (int i = getDimensions(); i > 0; --i) {
               b.append("[]");
            }
            return b.toString();
            // case OBJECT:
         default:
            return new String(buf, off, len).replace('/', '.');
         }
      }
   }


}