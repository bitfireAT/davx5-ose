package org.simpleframework.xml.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.ElementListUnion;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.Text;

public class ComplexDocumentTest extends TestCase {

   public static interface WordXMLTagsOperation {
   }
   
   public static class CTBackground
   {
      @Attribute
       private String color;    

       public String getColor() {
           return color;
       }

       public void setColor(String color) {
           this.color = color;
       }  
   }

   public static class CTBody
   {
      @ElementListUnion ( {
         @ElementList(entry="p", inline=true, type=CTP.class, required = false),
         @ElementList(entry="tbl", inline=true, type=CTTbl.class, required = false)
      })    
      List<WordXMLTagsOperation> operations;

      @Element(required=false)
      private CTSectPr sectPr;

      public List<WordXMLTagsOperation> getOperations() {
         return operations;
      }


      public CTSectPr getSectPr() {
         return sectPr;
      }

      public void setSectPr(CTSectPr sectPr) {
         this.sectPr = sectPr;
      }

      @Validate
      public void validate() {
      }
   }

   public static class CTCustomXmlPr
   {   
   }

   public static class CTCustomXmlRun extends EGPContent implements WordXMLTagsOperation
   {
      @Element(required=false)
       private CTCustomXmlPr customXmlPr;
       private List<EGPContent> EGPContentList = new ArrayList<EGPContent>();
       @Attribute(required=false)
       private String uri;
       @Attribute
       private String element;

       public CTCustomXmlPr getCustomXmlPr() {
           return customXmlPr;
       }

       public void setCustomXmlPr(CTCustomXmlPr customXmlPr) {
           this.customXmlPr = customXmlPr;
       }

       public List<EGPContent> getEGPContentList() {
           return EGPContentList;
       }

       public void setEGPContentList(List<EGPContent> list) {
           EGPContentList = list;
       }

       public String getUri() {
           return uri;
       }

       public void setUri(String uri) {
           this.uri = uri;
       }

       public String getElement() {
           return element;
       }

       public void setElement(String element) {
           this.element = element;
       } 
   }

   @Root(name="document")
   public static class CTDocument extends CTDocumentBase
   {
      @Element
       private CTBody body;

       public CTBody getBody() {
           return body;
       }

       public void setBody(CTBody body) {
           this.body = body;
       }
       
       @Validate
      public void validate() {
      }
   }

   public static class CTDocumentBase
   {
      @Element(name="background", required=false)
       private CTBackground background;

       public CTBackground getBackground() {
           return background;
       }

       public void setBackground(CTBackground background) {
           this.background = background;
       }
   }

   public static class CTHyperlink extends EGPContent implements WordXMLTagsOperation
   {    
       @Attribute(required=false)
       private String tgtFrame;
       @Attribute(required=false)
       private String tooltip;
       @Attribute(required=false)
       private String docLocation;
       @Attribute(required=false)
       private String history;
       @Attribute(required=false)
       private String anchor;
       @Attribute(required=false)
       private String id; 
      
       public String getTgtFrame() {
           return tgtFrame;
       }

       public void setTgtFrame(String tgtFrame) {
           this.tgtFrame = tgtFrame;
       }

       public String getTooltip() {
           return tooltip;
       }

       public void setTooltip(String tooltip) {
           this.tooltip = tooltip;
       }

       public String getDocLocation() {
           return docLocation;
       }

       public void setDocLocation(String docLocation) {
           this.docLocation = docLocation;
       }

       public String getHistory() {
           return history;
       }

       public void setHistory(String history) {
           this.history = history;
       }

       public String getAnchor() {
           return anchor;
       }

       public void setAnchor(String anchor) {
           this.anchor = anchor;
       }

       public String getId() {
           return id;
       }

       public void setId(String id) {
           this.id = id;
       }   
   }

   public static class CTP extends EGPContent implements WordXMLTagsOperation
   {  
       private List<EGPContent> EGPContentList = new ArrayList<EGPContent>();
       private byte[] _rsidR;
       private byte[] _rsidDel;
       private byte[] _rsidP;
       
       @Attribute(required=false)
       private String rsidRPr;
       
       @Attribute(required=false)
       private String rsidR;
       
       @Attribute(required=false)
       private String rsidDel;
       
       @Attribute(required=false)
       private String rsidP;
       
       @Attribute(required=false)
       private String rsidRDefault;
       
       @Element(name="pPr", required=false)
       private CTPPr PPr;   
     
       
       public CTPPr getPPr() {
           return PPr;
       }

       public void setPPr(CTPPr pPr) {
           PPr = pPr;
       }

       public List<EGPContent> getEGPContentList() {
           return EGPContentList;
       }

       public void setEGPContentList(List<EGPContent> list) {
           EGPContentList = list;
       }

       public String getRsidRPr() {
           return rsidRPr;
       }

       public void setRsidRPr(String rsidRPr) {
           this.rsidRPr = rsidRPr;
       }

       public String getRsidR() {
           return rsidR;
       }

       public void setRsidR(String rsidR) {
           this.rsidR = rsidR;
       }

       public String getRsidDel() {
           return rsidDel;
       }

       public void setRsidDel(String rsidDel) {
           this.rsidDel = rsidDel;
       }

       public String getRsidP() {
           return rsidP;
       }

       public void setRsidP(String rsidP) {
           this.rsidP = rsidP;
       }

       public String getRsidRDefault() {
           return rsidRDefault;
       }

       public void setRsidRDefault(String rsidRDefault) {
           this.rsidRDefault = rsidRDefault;
       }
       
       @Validate
      public void validate() {
      }
   }

   public static class CTPPr
   {
      @Element(required=false)
       private CTParaRPr rPr;
      @Element(required=false)
       private CTSectPr sectPr;
      

       public CTParaRPr getRPr() {
           return rPr;
       }

       public void setRPr(CTParaRPr rPr) {
         this.rPr = rPr;
       }

       public CTSectPr getSectPr() {
           return sectPr;
       }

       public void setSectPr(CTSectPr sectPr) {
           this.sectPr = sectPr;
       }   
   }

   public static class CTParaRPr
   {  
   }

   @Element(name="r")
   public static class CTR extends EGRunInnerContent implements WordXMLTagsOperation
   {
      @Element(required = false)
       private CTRPr rPr;
       private List<EGRunInnerContent> EGRunInnerContentList = new ArrayList<EGRunInnerContent>();
       private byte[] _rsidDel;
       
       @Attribute(required=false)
       private String rsidRPr;
       
       @Attribute(required=false)
       private String rsidDel;
       
       @Attribute(required=false)
       private String rsidR;

       public CTRPr getRPr() {
           return rPr;
       }

       public void setRPr(CTRPr rPr) {
           this.rPr = rPr;
       }

       public List<EGRunInnerContent> getEGRunInnerContentList() {
           return EGRunInnerContentList;
       }

       public void setEGRunInnerContentList(List<EGRunInnerContent> list) {
           EGRunInnerContentList = list;
       }

       public String getRsidRPr() {
           return rsidRPr;
       }

       public void setRsidRPr(String rsidRPr) {
           this.rsidRPr = rsidRPr;
       }

       public String getRsidDel() {
           return rsidDel;
       }

       public void setRsidDel(String rsidDel) {
           this.rsidDel = rsidDel;
       }

       public String getRsidR() {
           return rsidR;
       }

       public void setRsidR(String rsidR) {
           this.rsidR = rsidR;
       }  
   }

   public static class CTRPr 
   {  
   }

   public static class CTRel
   {
      @Attribute
       private String id;

       public String getId() {
           return id;
       }

       public void setId(String id) {
           this.id = id;
       }   
   }

   public static class CTSdtContentRun extends EGPContent
   {
   }

   public static class CTSdtEndPr
   {
       private List<CTRPr> RPrList = new ArrayList<CTRPr>();

       public List<CTRPr> getRPrList() {
           return RPrList;
       }

       public void setRPrList(List<CTRPr> list) {
           RPrList = list;
       }
   }

   public static class CTSdtPr
   {      
   }

   public static class CTSdtRun implements WordXMLTagsOperation
   {
      @Element(required=false)
       private CTSdtPr sdtPr;
      @Element(required=false)
       private CTSdtEndPr sdtEndPr;
      @Element(required=false)
       private CTSdtContentRun sdtContent;

       public CTSdtPr getSdtPr() {
           return sdtPr;
       }

       public void setSdtPr(CTSdtPr sdtPr) {
           this.sdtPr = sdtPr;
       }

       public CTSdtEndPr getSdtEndPr() {
           return sdtEndPr;
       }

       public void setSdtEndPr(CTSdtEndPr sdtEndPr) {
           this.sdtEndPr = sdtEndPr;
       }

       public CTSdtContentRun getSdtContent() {
           return sdtContent;
       }

       public void setSdtContent(CTSdtContentRun sdtContent) {
           this.sdtContent = sdtContent;
       } 
   }

   public static class CTSectPr
   {    
   }

   public static class CTSimpleField extends EGPContent implements WordXMLTagsOperation
   {
      @Element(required=false)
       private CTText fldData;
       private List<EGPContent> EGPContentList = new ArrayList<EGPContent>();
       @Attribute
       private String instr;   

       public CTText getFldData() {
           return fldData;
       }

       public void setFldData(CTText fldData) {
           this.fldData = fldData;
       }

       public List<EGPContent> getEGPContentList() {
           return EGPContentList;
       }

       public void setEGPContentList(List<EGPContent> list) {
           EGPContentList = list;
       }

       public String getInstr() {
           return instr;
       }

       public void setInstr(String instr) {
           this.instr = instr;
       }    
   }

   public static class CTSmartTagPr
   {
   }

   public static class CTSmartTagRun extends EGPContent implements WordXMLTagsOperation
   {
      @Element(required=false)
       private CTSmartTagPr smartTagPr;
       private List<EGPContent> EGPContentList = new ArrayList<EGPContent>();
       @Attribute(required=false)
       private String uri;
       @Attribute
       private String element;

       public CTSmartTagPr getSmartTagPr() {
           return smartTagPr;
       }

       public void setSmartTagPr(CTSmartTagPr smartTagPr) {
           this.smartTagPr = smartTagPr;
       }

       public List<EGPContent> getEGPContentList() {
           return EGPContentList;
       }

       public void setEGPContentList(List<EGPContent> list) {
           EGPContentList = list;
       }

       public String getUri() {
           return uri;
       }

       public void setUri(String uri) {
           this.uri = uri;
       }

       public String getElement() {
           return element;
       }

       public void setElement(String element) {
           this.element = element;
       }
   }

   public static class CTTbl implements WordXMLTagsOperation
   {  
       @Validate
      public void validate() {
      }
   }

   public static class CTText
   {
      @Text
       private String string;
      
      @Attribute(required=false)
       private String space;

       public String getString() {
           return string;
       }

       public void setString(String string) {
           this.string = string;
       }

       public String getSpace() {
           return space;
       }

       public void setSpace(String space) {
           this.space = space;
       }
   }

   public static class EGPContent
   {   

       @ElementListUnion({
            @ElementList(entry = "r", inline = true, type = CTR.class, required = false),
            @ElementList(entry = "hyperlink", inline = true, type = CTHyperlink.class, required = false),
            @ElementList(entry = "subDoc", inline = true, type = CTRel.class, required = false),
            @ElementList(entry = "customXml", inline = true, type = CTCustomXmlRun.class, required = false),
            @ElementList(entry = "smartTag", inline = true, type = CTSmartTagRun.class, required = false),
            @ElementList(entry = "fldSimple", inline = true, type = CTSimpleField.class, required = false),
            @ElementList(entry = "sdt", inline = true, type = CTSdtRun.class, required = false) })
      List<WordXMLTagsOperation> PContentList;

      public List<WordXMLTagsOperation> getOperations() {
         return PContentList;
      }

   }

   public static class EGRunInnerContent {

      @Element(name = "t", required = false)
      private CTText T;

       public CTText getT() {
           return T;
       }

       public void setT(CTText t) {        
           T = t;
       }    
   }

   
   public static InputStream openDocument() throws Exception {
      try {
         return ComplexDocumentTest.class.getClassLoader().getResourceAsStream("org/simpleframework/xml/core/document.xml");
      }catch(Exception e) {
         e.printStackTrace();
         return null;
      }
   }
   
   @SuppressWarnings("unused")
   public void testComplexDocument() throws Exception {
      Serializer serializer = new Persister();
      InputStream source = openDocument();
      
      if(source != null) {
         int size = source.available();
         
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         byte[] buffer = new byte[4096];
         int read = 0;        
         while( (read = source.read(buffer)) != -1 ) {
            baos.write(buffer, 0, read);
         }
         source.close();
         byte[] data = baos.toByteArray(); 
   
         InputStream stream = new ByteArrayInputStream(data);
         long start = System.currentTimeMillis();
         CTDocument doc = serializer.read(CTDocument.class, stream, false);
         System.err.println("Time taken was "+(System.currentTimeMillis() - start));
   
         stream = new ByteArrayInputStream(data);
         start = System.currentTimeMillis();
         doc = serializer.read(CTDocument.class, stream, false);
         System.err.println("Second time taken was "+(System.currentTimeMillis() - start));
         
         List<WordXMLTagsOperation> bodyContentList = doc.getBody().getOperations();
         
         assertFalse(bodyContentList.isEmpty());
      }
   }
}
