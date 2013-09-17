package org.simpleframework.xml.strategy;

import java.net.URI;


class PackageParser {
   
   private static final String scheme = "http://";
   
   public Class revert(String reference) throws Exception {
      URI uri = new URI(reference);
      String domain = uri.getHost();
      String path = uri.getPath();
      String[] list = domain.split("\\.");
      
      if(list.length > 1) {
         domain = list[1] + "." + list[0];
      } else {
         domain = list[0];
      }
      String type =  domain + path.replaceAll("\\/+", ".");
      return Class.forName(type);
   }
   
   public String parse(String className) throws Exception {
      return new Convert(className).fastParse();
   }
   
   public String parse(Class type) throws Exception {
      return new Convert(type.getName()).fastParse();
   }
   
   public static class Convert {
      private char[] array;
      private int count;
      private int mark; 
      private int size; 
      private int pos;
      
      public Convert(String type) {
         this.array = type.toCharArray();
      }
      
      public String fastParse() throws Exception {  
         char[] work = new char[array.length + 10];

         scheme(work);
         domain(work);
         path(work);
         
         return new String(work, 0, pos);
      }
      
      private void scheme(char[] work) {
         "http://".getChars(0, 7, work, 0);
         pos += 7;
      }
      
      private void path(char[] work) {
         for(int i = size; i < array.length; i++) {
            if(array[i] == '.') {
               work[pos++] = '/';
            } else {
               work[pos++] = array[i];
            }
         }
      }
      
      private void domain(char[] work) {
         while(size < array.length) { 
            if(array[size] == '.') { 
               if(count++ == 1) {
                  break;
               }
               mark = size + 1;
            }
            size++;
         }
         for(int i = 0; i < size - mark; i++) {
            work[pos++] = array[mark + i];
         }
         work[pos++] = '.';
         work[size + 7] = '/';
         
         for(int i = 0; i < mark - 1; i++) {
            work[pos++] = array[i];
         }
      }
   }
}