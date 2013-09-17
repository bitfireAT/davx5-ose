package org.simpleframework.xml.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

public class SignatureBuilderTest extends TestCase {

   private class SignatureBuilder {
      
      private List<List<String>> parameters;
      
      public SignatureBuilder(List<List<String>> parameters) {
         this.parameters = parameters;
      }
      
      public List<List<String>> build() {
         List<List<String>> matrixToBeBuilt = new ArrayList<List<String>>();
         for(int i = 0; i < parameters.size(); i++) {
            matrixToBeBuilt.add(new ArrayList<String>());
         }
         build(0, 0, matrixToBeBuilt);
         return matrixToBeBuilt;
      }
      
      public void build(int rowIndex, int columnIndex, List<List<String>> newMatrix) {
         build(new ArrayList<String>(), columnIndex, newMatrix);
      }
      
      public void build(List<String> signature, int columnIndex, List<List<String>> newMatrix) {
         List<String> parametersAtTheCurrentIndex = parameters.get(columnIndex);
         
         if(parameters.size() - 1 > columnIndex) {
            for(int i = 0; i < parametersAtTheCurrentIndex.size(); i++) {
               List<String> newSignature = new ArrayList<String>(signature);
               String parameter = parametersAtTheCurrentIndex.get(i);
               newSignature.add(parameter);
               build(newSignature, columnIndex+1, newMatrix);
            }
         } else {

            for(int i = 0; i < parametersAtTheCurrentIndex.size(); i++) {
               for(int j = 0; j < signature.size(); j++) {
                  List<String> columnInMatrix = newMatrix.get(j);
                  String value = signature.get(j);
                  columnInMatrix.add(value);
               }
               List<String> lastColumnInMatrix = newMatrix.get(columnIndex); 
               String valueInLastColumn = parametersAtTheCurrentIndex.get(i);
               lastColumnInMatrix.add(valueInLastColumn);
            }
         }
      }
   }
   
   public void testMatrix() {
      List<String> first = Arrays.asList("x", "y");
      List<String> second = Arrays.asList("a", "b", "c");
      List<String> third = Arrays.asList("1", "2");
      List<String> fourth = Arrays.asList("i", "j");
      List<List<String>> list = new ArrayList<List<String>>();
      list.add(first);
      list.add(second);
      list.add(third);
      list.add(fourth);
      SignatureBuilder builder = new SignatureBuilder(list);
      List<List<String>> matrix = builder.build();
      
      int rowSize = matrix.get(0).size();
      int columnCount = matrix.size();
      for(int rowIndex = 0; rowIndex < rowSize; rowIndex++) {
         for(int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
            System.err.print("["+matrix.get(columnIndex).get(rowIndex)+"] ");
         }
         System.err.println();
      }

   }
   
}
