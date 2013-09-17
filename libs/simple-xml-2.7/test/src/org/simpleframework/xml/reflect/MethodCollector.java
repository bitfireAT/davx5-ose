package org.simpleframework.xml.reflect;

/**
 * Objects of this class collects information from a specific method.
 * 
 * @author Guilherme Silveira
 */
class MethodCollector {

   private final int paramCount;

   private final int ignoreCount;

   private int currentParameter;

   private final StringBuffer result;

   private boolean debugInfoPresent;

   public MethodCollector(int ignoreCount, int paramCount) {
      this.ignoreCount = ignoreCount;
      this.paramCount = paramCount;
      this.result = new StringBuffer();
      this.currentParameter = 0;
      // if there are 0 parameters, there is no need for debug info
      this.debugInfoPresent = paramCount == 0;
   }

   public void visitLocalVariable(String name, int index) {
      if (index >= ignoreCount && index < ignoreCount + paramCount) {
         if (!name.equals("arg" + currentParameter)) {
            debugInfoPresent = true;
         }
         result.append(',');
         result.append(name);
         currentParameter++;
      }
   }

   public String getResult() {
      return result.length() != 0 ? result.substring(1) : "";
   }

   public boolean isDebugInfoPresent() {
      return debugInfoPresent;
   }

}