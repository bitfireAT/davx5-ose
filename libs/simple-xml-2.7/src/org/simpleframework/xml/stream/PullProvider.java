/*
 * PullProvider.java January 2010
 *
 * Copyright (C) 2010, Niall Gallagher <niallg@users.sf.net>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or 
 * implied. See the License for the specific language governing 
 * permissions and limitations under the License.
 */

package org.simpleframework.xml.stream;

import java.io.InputStream;
import java.io.Reader;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
 
/**
 * The <code>PullProvider</code> class is used to provide an event
 * reader that uses the XML pull API available on Google Android. It
 * provides the best performance on Android as it avoids having to
 * build a full DOM model. The <code>EventReader</code> produced by
 * this provider will have full namespace capabilities and also has
 * line numbers available for each of the events that are extracted.
 * 
 * @author Niall Gallagher
 */
class PullProvider implements Provider {
   
   /**
    * This is used to create a new XML pull parser for the reader.
    */
   private final XmlPullParserFactory factory;
   
   /**
    * Constructor for the <code>PullProvider</code> object. This
    * will instantiate a namespace aware pull parser factory that
    * will be used to parse the XML documents that are read by
    * the framework. If XML pull is not available this will fail.
    */
   public PullProvider() throws Exception {
      this.factory = XmlPullParserFactory.newInstance();
      this.factory.setNamespaceAware(true);
   }
   
   /**
    * This provides an <code>EventReader</code> that will read from
    * the specified input stream. When reading from an input stream
    * the character encoding should be taken from the XML prolog or
    * it should default to the UTF-8 character encoding.
    * 
    * @param source this is the stream to read the document with
    * 
    * @return this is used to return the event reader implementation
    */
   public EventReader provide(InputStream source) throws Exception {
      XmlPullParser parser = factory.newPullParser();
      
      if(source != null) {
         parser.setInput(source, null);
      }
      return new PullReader(parser);  
   }
   
   /**
    * This provides an <code>EventReader</code> that will read from
    * the specified reader. When reading from a reader the character
    * encoding should be the same as the source XML document.
    * 
    * @param source this is the reader to read the document with
    * 
    * @return this is used to return the event reader implementation
    */
   public EventReader provide(Reader source) throws Exception {
      XmlPullParser parser = factory.newPullParser();
      
      if(source != null) {
         parser.setInput(source);
      }
      return new PullReader(parser);
   }
}
