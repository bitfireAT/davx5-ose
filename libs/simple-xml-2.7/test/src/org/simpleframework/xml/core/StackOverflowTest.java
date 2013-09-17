package org.simpleframework.xml.core;

import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;

public class StackOverflowTest extends ValidationTestCase {
   
   private static final int ITERATIONS = 1000;
   
   private static final String NEW_BENEFIT = 
   "<newBenefit>"+
   "   <office>AAAAA</office>"+
   "   <recordNumber>1046</recordNumber>"+
   "   <type>A</type>"+
   "</newBenefit>";
   
   private static final String BENEFIT_MUTATION = 
   "<benefitMutation>"+
   "   <office>AAAAA</office>"+
   "   <recordNumber>1046</recordNumber>"+
   "   <type>A</type>"+
   "   <comment>comment</comment>"+
   "</benefitMutation>";

   @Root
   public static class Delivery {

      @ElementList(inline = true, required = false, name = "newBenefit")
      private List<NewBenefit> listNewBenefit = new ArrayList<NewBenefit>();

      @ElementList(inline = true, required = false, name = "benefitMutation")
      private List<BenefitMutation> listBenefitMutation = new ArrayList<BenefitMutation>();

   }

   public static class NewBenefit {

      @Element
      private String office;

      @Element
      private String recordNumber;

      @Element
      private String type;
   }

   public static class BenefitMutation extends NewBenefit {

      @Element(required = false)
      private String comment;
   }
   
   public void testStackOverflow() throws Exception {
      StringBuilder builder = new StringBuilder();
      Persister persister = new Persister();
      builder.append("<delivery>");
      
      boolean isNewBenefit = true;
      for(int i = 0; i < ITERATIONS; i++) {
         String text = null;

         if(isNewBenefit) {
            text = NEW_BENEFIT;
         } else {
            text = BENEFIT_MUTATION;
         }
         isNewBenefit = !isNewBenefit ;
         builder.append(text);
      }
      builder.append("</delivery>");
      
      Delivery delivery = persister.read(Delivery.class, builder.toString());
      
      assertEquals(delivery.listBenefitMutation.size() + delivery.listNewBenefit.size(), ITERATIONS);
      
      validate(persister, delivery);
   }
   

}
