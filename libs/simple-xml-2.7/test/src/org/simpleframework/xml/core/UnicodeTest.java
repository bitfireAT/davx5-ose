package org.simpleframework.xml.core;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.util.Dictionary;
import org.simpleframework.xml.util.Entry;

public class UnicodeTest extends ValidationTestCase {

   private static final String SOURCE =     
   "<?xml version='1.0' encoding='UTF-8'?>\n"+      
   "<example>\n"+
   "   <list>\n"+
   "      <unicode origin=\"Australia\" name=\"Nicole Kidman\">\n"+
   "         <text>Nicole Kidman</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Austria\" name=\"Johann Strauss\">\n"+
   "         <text>Johann Strauß</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Canada\" name=\"Celine Dion\">\n"+
   "         <text>Céline Dion</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Democratic People's Rep. of Korea\" name=\"LEE Sol-Hee\">\n"+
   "         <text>이설희</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Denmark\" name=\"Soren Hauch-Fausboll\">\n"+
   "         <text>Søren Hauch-Fausbøll</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Denmark\" name=\"Soren Kierkegaard\">\n"+
   "         <text>Søren Kierkegård</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Egypt\" name=\"Abdel Halim Hafez\">\n"+
   "         <text>ﻋﺑﺪﺍﻠﺣﻟﻳﻢ ﺤﺎﻓﻅ</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Egypt\" name=\"Om Kolthoum\">\n"+
   "         <text>ﺃﻡ ﻛﻟﺛﻭﻡ</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Eritrea\" name=\"Berhane Zeray\">\n"+
   "         <text>ኤርትራ</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Ethiopia\" name=\"Haile Gebreselassie\">\n"+
   "         <text>ኢትዮጵያ</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"France\" name=\"Gerard Depardieu\">\n"+
   "         <text>Gérard Depardieu</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"France\" name=\"Jean Reno\">\n"+
   "         <text>Jean Réno</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"France\" name=\"Camille Saint-Saens\">\n"+
   "         <text>Camille Saint-Saëns</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"France\" name=\"Mylene Demongeot\">\n"+
   "         <text>Mylène Demongeot</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"France\" name=\"Francois Truffaut\">\n"+
   "         <text>François Truffaut</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Germany\" name=\"Rudi Voeller\">\n"+
   "         <text>Rudi Völler</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Germany\" name=\"Walter Schultheiss\">\n"+
   "         <text>Walter Schultheiß</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Greece\" name=\"Giorgos Dalaras\">\n"+
   "         <text>Γιώργος Νταλάρας</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Iceland\" name=\"Bjork Gudmundsdottir\">\n"+
   "         <text>Björk Guðmundsdóttir</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"India (Hindi)\" name=\"Madhuri Dixit\">\n"+
   "         <text>माधुरी दिछित</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Ireland\" name=\"Sinead O'Connor\">\n"+
   "         <text>Sinéad O'Connor</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Israel\" name=\"Yehoram Gaon\">\n"+
   "         <text>יהורם גאון</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Italy\" name=\"Fabrizio DeAndre\">\n"+
   "         <text>Fabrizio De André</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Japan\" name=\"KUBOTA Toshinobu\">\n"+
   "         <text>久保田    利伸</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Japan\" name=\"HAYASHIBARA Megumi\">\n"+
   "         <text>林原 めぐみ</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Japan\" name=\"Mori Ogai\">\n"+
   "         <text>森鷗外</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Japan\" name=\"Tex Texin\">\n"+
   "         <text>テクス テクサン</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Norway\" name=\"Tor Age Bringsvaerd\">\n"+
   "         <text>Tor Åge Bringsværd</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Pakistan (Urdu)\" name=\"Nusrat Fatah Ali Khan\">\n"+
   "         <text>نصرت فتح علی خان</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"People's Rep. of China\" name=\"ZHANG Ziyi\">\n"+
   "         <text>章子怡</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"People's Rep. of China\" name=\"WONG Faye\">\n"+
   "         <text>王菲</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Poland\" name=\"Lech Walesa\">\n"+
   "         <text>Lech Wałęsa</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Puerto Rico\" name=\"Olga Tanon\">\n"+
   "         <text>Olga Tañón</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Rep. of China\" name=\"Hsu Chi\">\n"+
   "         <text>舒淇</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Rep. of China\" name=\"Ang Lee\">\n"+
   "         <text>李安</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Rep. of Korea\" name=\"AHN Sung-Gi\">\n"+
   "         <text>안성기</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Rep. of Korea\" name=\"SHIM Eun-Ha\">\n"+
   "         <text>심은하</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Russia\" name=\"Mikhail Gorbachev\">\n"+
   "         <text>Михаил Горбачёв</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Russia\" name=\"Boris Grebenshchikov\">\n"+
   "         <text>Борис Гребенщиков</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Syracuse (Sicily)\" name=\"Archimedes\">\n"+
   "         <text>Ἀρχιμήδης</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"Thailand\" name=\"Thongchai McIntai\">\n"+
   "         <text>ธงไชย แม็คอินไตย์</text>\n"+
   "      </unicode>\n"+
   "      <unicode origin=\"U.S.A.\" name=\"Brad Pitt\">\n"+
   "         <text>Brad Pitt</text>\n"+
   "      </unicode>\n"+
   "   </list>\n"+
   "</example>\n"; 
   
   @Root(name="unicode")
   private static class Unicode implements Entry {

      @Attribute(name="origin")
      private String origin;

      @Element(name="text")
      private String text;
      
      @Attribute
      private String name;
      
      public String getName() {
         return name;
      }
   }

   @Root(name="example")
   private static class UnicodeExample {

      @ElementList(name="list", type=Unicode.class)
      private Dictionary<Unicode> list;

      public Unicode get(String name) {
         return list.get(name);              
      }
   }

   private Persister persister;

   public void setUp() throws Exception {
      persister = new Persister();
   }
	
   public void testUnicode() throws Exception {    
      UnicodeExample example = persister.read(UnicodeExample.class, SOURCE);

      assertUnicode(example);
      validate(example, persister); // Ensure the deserialized object is valid
   }

   public void testWriteUnicode() throws Exception {
      UnicodeExample example = persister.read(UnicodeExample.class, SOURCE);

      assertUnicode(example);
      validate(example, persister); // Ensure the deserialized object is valid

      StringWriter out = new StringWriter();
      persister.write(example, out);
      example = persister.read(UnicodeExample.class, out.toString());

      assertUnicode(example);
      validate(example, persister);      
   }
   
   public void testUnicodeFromByteStream() throws Exception {
      byte[] data = SOURCE.getBytes("UTF-8");
      InputStream source = new ByteArrayInputStream(data);
      UnicodeExample example = persister.read(UnicodeExample.class, source);

      assertUnicode(example);
      validate(example, persister); // Ensure the deserialized object is valid
   }

   public void testIncorrectEncoding() throws Exception {
      byte[] data = SOURCE.getBytes("UTF-8");
      InputStream source = new ByteArrayInputStream(data);
      UnicodeExample example = persister.read(UnicodeExample.class, new InputStreamReader(source, "ISO-8859-1"));

      assertFalse("Encoding of ISO-8859-1 did not work", isUnicode(example));
   }
   
   public void assertUnicode(UnicodeExample example) throws Exception {
      assertTrue("Data was not unicode", isUnicode(example));
   }
   
   public boolean isUnicode(UnicodeExample example) throws Exception {
      // Ensure text remailed unicode           
      if(!example.get("Nicole Kidman").text.equals("Nicole Kidman")) return false;
      if(!example.get("Johann Strauss").text.equals("Johann Strauß")) return false;
      if(!example.get("Celine Dion").text.equals("Céline Dion")) return false;
      if(!example.get("LEE Sol-Hee").text.equals("이설희")) return false;
      if(!example.get("Soren Hauch-Fausboll").text.equals("Søren Hauch-Fausbøll")) return false;
      if(!example.get("Soren Kierkegaard").text.equals("Søren Kierkegård")) return false;
      if(!example.get("Abdel Halim Hafez").text.equals("ﻋﺑﺪﺍﻠﺣﻟﻳﻢ ﺤﺎﻓﻅ")) return false;
      if(!example.get("Om Kolthoum").text.equals("ﺃﻡ ﻛﻟﺛﻭﻡ")) return false;
      if(!example.get("Berhane Zeray").text.equals("ኤርትራ")) return false;
      if(!example.get("Haile Gebreselassie").text.equals("ኢትዮጵያ")) return false;
      if(!example.get("Gerard Depardieu").text.equals("Gérard Depardieu")) return false;
      if(!example.get("Jean Reno").text.equals("Jean Réno")) return false;
      if(!example.get("Camille Saint-Saens").text.equals("Camille Saint-Saëns")) return false;
      if(!example.get("Mylene Demongeot").text.equals("Mylène Demongeot")) return false;
      if(!example.get("Francois Truffaut").text.equals("François Truffaut")) return false;
      //if(!example.get("Rudi Voeller").text.equals("Rudi Völler")) return false;
      if(!example.get("Walter Schultheiss").text.equals("Walter Schultheiß")) return false;
      if(!example.get("Giorgos Dalaras").text.equals("Γιώργος Νταλάρας")) return false;
      if(!example.get("Bjork Gudmundsdottir").text.equals("Björk Guðmundsdóttir")) return false;
      if(!example.get("Madhuri Dixit").text.equals("माधुरी दिछित")) return false;
      if(!example.get("Sinead O'Connor").text.equals("Sinéad O'Connor")) return false;
      if(!example.get("Yehoram Gaon").text.equals("יהורם גאון")) return false;
      if(!example.get("Fabrizio DeAndre").text.equals("Fabrizio De André")) return false;
      if(!example.get("KUBOTA Toshinobu").text.equals("久保田    利伸")) return false;
      if(!example.get("HAYASHIBARA Megumi").text.equals("林原 めぐみ")) return false;
      if(!example.get("Mori Ogai").text.equals("森鷗外")) return false;
      if(!example.get("Tex Texin").text.equals("テクス テクサン")) return false;
      if(!example.get("Tor Age Bringsvaerd").text.equals("Tor Åge Bringsværd")) return false;
      if(!example.get("Nusrat Fatah Ali Khan").text.equals("نصرت فتح علی خان")) return false;
      if(!example.get("ZHANG Ziyi").text.equals("章子怡")) return false;
      if(!example.get("WONG Faye").text.equals("王菲")) return false;
      if(!example.get("Lech Walesa").text.equals("Lech Wałęsa")) return false;
      if(!example.get("Olga Tanon").text.equals("Olga Tañón")) return false;
      if(!example.get("Hsu Chi").text.equals("舒淇")) return false;
      if(!example.get("Ang Lee").text.equals("李安")) return false;
      if(!example.get("AHN Sung-Gi").text.equals("안성기")) return false;
      if(!example.get("SHIM Eun-Ha").text.equals("심은하")) return false;
      if(!example.get("Mikhail Gorbachev").text.equals("Михаил Горбачёв")) return false;
      if(!example.get("Boris Grebenshchikov").text.equals("Борис Гребенщиков")) return false;
      if(!example.get("Archimedes").text.equals("Ἀρχιμήδης")) return false;
      if(!example.get("Thongchai McIntai").text.equals("ธงไชย แม็คอินไตย์")) return false;
      if(!example.get("Brad Pitt").text.equals("Brad Pitt")) return false;
      return true;
   }
}
