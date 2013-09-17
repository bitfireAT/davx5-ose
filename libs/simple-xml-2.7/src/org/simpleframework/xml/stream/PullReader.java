/*
 * PullReader.java January 2010
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

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;
import static org.xmlpull.v1.XmlPullParser.TEXT;

import org.xmlpull.v1.XmlPullParser;

/**
 * The <code>PullReader</code> is used to provide an event reader 
 * thats makes use of the XML pull API. This provides the best
 * performance on the Google Android platform. Providing support
 * for the XML pull API ensures that a full DOM model does not
 * need to be build for large documents. It also ensures that
 * line numbers are available when each event is extracted.
 * 
 * @author Niall Gallagher
 */
class PullReader implements EventReader {

   /**
    * This is the XML pull parser that is used to create events.
    */
   private XmlPullParser parser;
   
   /**
    * This is the previous node that has been read by a peek.
    */
   private EventNode peek;
   
   /**
    * Constructor for the <code>PullReader</code> object. This is
    * provided an XML pull parser which has been initialized with
    * the XML source document. Each even produced by this reader
    * will be extracted from the provided XML pull parser.
    * 
    * @param parser this is the parser used to create all events
    */
   public PullReader(XmlPullParser parser) {
      this.parser = parser;
   }

   /**
    * This is used to peek at the node from the document. This will
    * scan through the document, ignoring any comments to find the
    * next relevant XML event to acquire. Typically events will be
    * the start and end of an element, as well as any text nodes.
    * 
    * @return this returns the next event taken from the source XML
    */
   public EventNode peek() throws Exception {
      if(peek == null) {
         peek = next();
      }
      return peek;
   }
   
   /**
    * This is used to take the next node from the document. This will
    * scan through the document, ignoring any comments to find the
    * next relevant XML event to acquire. Typically events will be
    * the start and end of an element, as well as any text nodes.
    * 
    * @return this returns the next event taken from the source XML
    */
   public EventNode next() throws Exception {
      EventNode next = peek;

      if(next == null) {
         next = read();
      } else {
         peek = null;
      }
      return next;
   }
   
   /**
    * This is used to read the next node from the document. This will
    * scan through the document, ignoring any comments to find the
    * next relevant XML event to acquire. Typically events will be
    * the start and end of an element, as well as any text nodes.
    * 
    * @return this returns the next event taken from the document 
    */
   private EventNode read() throws Exception {
      int event = parser.next();      
      
      if(event != END_DOCUMENT) {
         if(event == START_TAG){
            return start();
         }
         if(event == TEXT) {
            return text();
         }
         if(event == END_TAG) {
            return end();
         }
         return read();
      }
      return null;
   }

   /**
    * This is used to convert the next event to a text event. The
    * conversion process ensures the node can be digested by the core
    * reader and used to provide an <code>InputNode</code> that can
    * be used to represent an XML attribute within the source document.
    *
    * @return this returns the text event from the XML pull parser
    */
   private Text text() throws Exception {
      return new Text(parser);
   }
   
   /**
    * This is used to convert the next event to a start event. The
    * conversion process ensures the node can be digested by the core
    * reader and used to provide an <code>InputNode</code> that can
    * be used to represent an XML elements within the source document.
    *
    * @return this returns a start event created from the parser
    */
   private Start start() throws Exception {
      Start event = new Start(parser);
      
      if(event.isEmpty()) {
         return build(event);
      }
      return event;
   }
   
   /**
    * This is used to build the attributes that are to be used to 
    * populate the start event. Populating the start event with the
    * attributes it contains is required so that each element will
    * contain its associated attributes. Only attributes that are
    * not reserved will be added to the start event.
    * 
    * @param event this is the start event that is to be populated
    * 
    * @return this returns a start event with its attributes
    */
   private Start build(Start event) throws Exception {
      int count = parser.getAttributeCount();
      
      for(int i = 0; i < count; i++) {
         Entry entry = attribute(i);

         if(!entry.isReserved()) {
            event.add(entry);
         }
      }
      return event;
   }
   
   /**
    * This is used to convert the provided object to an attribute. The
    * conversion process ensures the node can be digested by the core
    * reader and used to provide an <code>InputNode</code> that can
    * be used to represent an XML attribute within the source document.
    * 
    * @param index this is the index of this attribute to create
    *
    * @return this returns an attribute created from the given object
    */
   private Entry attribute(int index) throws Exception {
      return new Entry(parser, index);
   }
   
   /**
    * This is used to create an event to signify that an element has
    * just ended. End events are important as they allow the core
    * reader to determine if a node is still in context. This provides
    * a more convenient way to use <code>InputNode</code> objects as
    * they should only ever be able to extract their children. 
    * 
    * @return this returns an end event to signify an element close
    */
   private End end() throws Exception {
      return new End();
   } 
   
   /**
    * The <code>Entry</code> object is used to represent an attribute
    * within a start element. This holds the name and value of the
    * attribute as well as the namespace prefix and reference. These
    * details can be used to represent the attribute so that should
    * the core reader require these details they can be acquired.
    * 
    * @author Niall Gallagher
    */
   private static class Entry extends EventAttribute {
      
      /**
       * This is the XML pull parser source that is used for this.
       */
      private final XmlPullParser source;
      
      /**
       * This is the XML namespace reference used by the attribute.
       */
      private final String reference;
      
      /**
       * This is the XML namespace prefix used by the attribute.
       */
      private final String prefix;
      
      /**
       * This is the name that is used by this attribute.
       */
      private final String name;
      
      /**
       * This is the value that is used by this attribute.
       */
      private final String value;
      
      /**
       * Constructor for the <code>Entry</code> object. This creates
       * an attribute object that is used to extract the name, value
       * namespace prefix, and namespace reference from the provided
       * node. This is used to populate any start events created.
       * 
       * @param source this is the parser used to get the attribute
       * @param index this is the index of the attribute to get
       */
      public Entry(XmlPullParser source, int index) {
         this.reference = source.getAttributeNamespace(index);
         this.prefix = source.getAttributePrefix(index);
         this.value = source.getAttributeValue(index);
         this.name = source.getAttributeName(index);
         this.source = source;
      }
      
      /**
       * This provides the name of the attribute. This will be the
       * name of the XML attribute without any namespace prefix. If
       * the name begins with "xml" then this attribute is reserved.
       * according to the namespaces for XML 1.0 specification.
       * 
       * @return this returns the name of this attribute object
       */
      public String getName() {
         return name;
      }
      
      /**
       * This returns the value of the event. This will be the value
       * that the attribute contains. If the attribute does not have
       * a value then this returns null or an empty string.
       * 
       * @return this returns the value represented by this object
       */
      public String getValue() {
         return value;
      }
      
      /**
       * This returns true if the attribute is reserved. An attribute
       * is considered reserved if it begins with "xml" according to 
       * the namespaces in XML 1.0 specification. Such attributes are
       * used for namespaces and other such details.
       *
       * @return this returns true if the attribute is reserved
       */
      public boolean isReserved() {
         return false;
      }
      
      /**
       * This is used to acquire the namespace reference that this 
       * attribute is in. A namespace is normally associated with an
       * attribute if that attribute is prefixed with a known token.
       * If there is no prefix then this will return null.
       * 
       * @return this provides the associated namespace reference
       */
      public String getReference() {
         return reference;
      }
      
      /**
       * This is used to acquire the namespace prefix associated with
       * this attribute. A prefix is used to qualify the attribute
       * within a namespace. So, if this has a prefix then it should
       * have a reference associated with it.
       * 
       * @return this returns the namespace prefix for the attribute
       */
      public String getPrefix() {
         return prefix;
      }
      
      /**
       * This is used to return the node for the attribute. Because 
       * this represents an XML event the XML parser is returned.
       * Returning the node helps with certain debugging issues.
       * 
       * @return this will return the source object for this
       */
      public Object getSource() {
         return source;
      }
   }
   
   /**
    * The <code>Start</code> object is used to represent the start of
    * an XML element. This will hold the attributes associated with
    * the element and will provide the name, the namespace reference
    * and the namespace prefix. For debugging purposes the source XML
    * element is provided for this start event.
    * 
    * @author Niall Gallagher
    */
   private static class Start extends EventElement {
      
      /**
       * This is the XML pull parser source that is used for this.
       */
      private final XmlPullParser source;
      
      /**
       * This is the XML namespace reference used by the attribute.
       */
      private final String reference;
      
      /**
       * This is the XML namespace prefix used by the attribute.
       */
      private final String prefix;
      
      /**
       * This is the name that is used by this attribute.
       */
      private final String name;
      
      /**
       * This is the line number where this element was read.
       */
      private final int line;
      
      /**
       * Constructor for the <code>Start</code> object. This will 
       * wrap the provided node and expose the required details such
       * as the name, namespace prefix and namespace reference. The
       * provided element node can be acquired for debugging purposes.
       * 
       * @param source this is the parser being wrapped by this
       */
      public Start(XmlPullParser source) {
         this.reference = source.getNamespace();
         this.line = source.getLineNumber();
         this.prefix = source.getPrefix();
         this.name = source.getName();
         this.source = source;     
      }
      
      /**
       * This is used to provide the line number the XML event was
       * encountered at within the XML document. If there is no line
       * number available for the node then this will return a -1.
       * 
       * @return this returns the line number if it is available
       */
      public int getLine() {
         return line;
      }
      
      /**
       * This provides the name of the event. This will be the name 
       * of an XML element the event represents. If there is a prefix
       * associated with the element, this extracts that prefix.
       * 
       * @return this returns the name without the namespace prefix
       */
      public String getName() {
         return name;
      }
      
      /**
       * This is used to acquire the namespace reference that this 
       * node is in. A namespace is normally associated with an XML
       * element or attribute, so text events and element close events
       * are not required to contain any namespace references. 
       * 
       * @return this will provide the associated namespace reference
       */
      public String getReference() {
         return reference;
      }
      
      /**
       * This is used to acquire the namespace prefix associated with
       * this node. A prefix is used to qualify an XML element or
       * attribute within a namespace. So, if this represents a text
       * event then a namespace prefix is not required.
       * 
       * @return this returns the namespace prefix for this event
       */
      public String getPrefix() {
         return prefix;
      }

      /**
       * This is used to return the node for the element. Because 
       * this represents an XML event the XML parser is returned.
       * Returning the node helps with certain debugging issues.
       * 
       * @return this will return the source object for this
       */
      public Object getSource() {
         return source;
      }
   }
   
   /**
    * The <code>Text</code> object is used to represent a text event.
    * If wraps a node that holds text consumed from the document. 
    * These are used by <code>InputNode</code> objects to extract the
    * text values for elements For debugging this exposes the node.
    * 
    * @author Niall Gallagher
    */
   private static class Text extends EventToken {
      
      /**
       * This is the XML pull parser used to extract this text event.
       */
      private final XmlPullParser source;
      
      /**
       * This is the text value that has been extracted from the XML.
       */
      private final String text;
      
      /**
       * Constructor for the <code>Text</code> object. This creates
       * an event that provides text to the core reader. Text can be
       * in the form of a CDATA section or a normal text entry.
       * 
       * @param source this is the node that represents the text value
       */
      public Text(XmlPullParser source){
         this.text = source.getText(); 
         this.source = source;
      }
      
      /**
       * This is true as this event represents a text token. Text 
       * tokens are required to provide a value only. So namespace
       * details and the node name will always return null.
       *  
       * @return this returns true as this event represents text  
       */
      public boolean isText() {
         return true;
      }
      
      /**
       * This returns the value of the event. This will return the
       * text value contained within the node. If there is no
       * text within the node this should return an empty string. 
       * 
       * @return this returns the value represented by this event
       */
      public String getValue(){
         return text;
      }
      
      /**
       * This is used to return the node for the text. Because 
       * this represents an XML event the XML parser is returned.
       * Returning the node helps with certain debugging issues.
       * 
       * @return this will return the source object for this
       */
      public Object getSource() {
         return source;
      }
   } 
   
   /**
    * The <code>End</code> object is used to represent the end of an
    * element. It is used by the core reader to determine which nodes
    * are in context and which ones are out of context. This allows
    * the input nodes to determine if it can read any more children.
    * 
    * @author Niall Gallagher
    */
   private static class End extends EventToken {
      
      /**
       * This is true as this event represents an element end. Such
       * events are required by the core reader to determine if a 
       * node is still in context. This helps to determine if there
       * are any more children to be read from a specific node.
       * 
       * @return this returns true as this token represents an end
       */
      public boolean isEnd() {
         return true;
      }
   }
}
