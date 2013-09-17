package org.simpleframework.xml.util;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;


public class Replace extends LineStripper {
   
   private static final String LGPL = 
      " \\* This library is free software.*02111-1307\\s+USA\\s+\\*\\/";
   
   private static final String APACHE =
      " * Licensed under the Apache License, Version 2.0 (the \"License\");\n"+
      " * you may not use this file except in compliance with the License.\n"+
      " * You may obtain a copy of the License at\n"+
      " *\n"+
      " *     http://www.apache.org/licenses/LICENSE-2.0\n"+
      " *\n"+
      " * Unless required by applicable law or agreed to in writing, software\n"+
      " * distributed under the License is distributed on an \"AS IS\" BASIS,\n"+
      " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or \n"+
      " * implied. See the License for the specific language governing \n"+
      " * permissions and limitations under the License.\n"+
      " */";

      

   public static void main(String[] list) throws Exception{
      List<File> files = getFiles(new File(list[0]), true);
      Pattern pattern = Pattern.compile(LGPL, Pattern.DOTALL | Pattern.MULTILINE);
      for(File file : files) {
         String text = getFile(file);
         text = pattern.matcher(text).replaceAll(APACHE);
         save(file, text);
      }
   }
   public static void save(File file, String text) throws Exception {
      OutputStream out = new FileOutputStream(file);
      OutputStreamWriter utf = new OutputStreamWriter(out, "UTF-8");
      utf.write(text);
      utf.flush();
      utf.close();
      out.flush();
      out.close();
   }
   public static String getFile(File file) throws Exception {
      InputStream in = new FileInputStream(file);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] block = new byte[8192];
      int count = 0;
      
      while((count = in.read(block)) != -1) {
         out.write(block, 0, count);
      }
      return out.toString("UTF-8");
   }
   public static List<File> getFiles(File root, boolean recursive) {
      List<File> files = new ArrayList<File>();
      File[] fileList = root.listFiles();
      for(File file : fileList) {
         if(file.isDirectory() && !file.getName().equals(".svn")) {
            if(recursive) {
               files.addAll(getFiles(file, recursive));
            }
         } else if(file.getName().endsWith(".java")){
            files.add(file);
         }
      }
      return files;
   }
}

