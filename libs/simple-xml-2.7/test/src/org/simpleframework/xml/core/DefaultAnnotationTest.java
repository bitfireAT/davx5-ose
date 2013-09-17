package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Default;
import org.simpleframework.xml.DefaultType;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.Transient;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.strategy.TreeStrategy;
import org.simpleframework.xml.stream.Format;

public class DefaultAnnotationTest extends ValidationTestCase {
   
   private static final String SOURCE =
   "<orderList id='100' array='a, b, c, d'>"+
   "   <value>Some Example Value</value>"+
   "   <orders>"+
   "      <orderItem>"+
   "         <name>IR1234</name>" +
   "         <value>2</value>"+
   "         <price>7.4</price>"+
   "         <customer id='1'>"+
   "            <name>John Doe</name>"+
   "            <address>Sin City</address>"+
   "         </customer>"+
   "      </orderItem>"+
   "      <orderItem>"+
   "         <name>TZ346</name>" +
   "         <value>2</value>"+
   "         <price>10.4</price>"+
   "         <customer id='2'>"+
   "            <name>Jane Doe</name>"+
   "           <address>Sesame Street</address>"+
   "         </customer>"+
   "      </orderItem>"+
   "   </orders>"+
   "</orderList>";
   
   private static final String ORDER =
   "<orderItem>"+
   "   <name>IR1234</name>" +
   "   <value>2</value>"+
   "   <price>7.4</price>"+
   "   <customer id='1'>"+
   "      <name>John Doe</name>"+
   "      <address>Sin City</address>"+
   "   </customer>"+
   "</orderItem>";
   
   private static final String MISMATCH = 
   "<typeMisMatch/>";
      
   
   @Root
   @Default(DefaultType.PROPERTY)
   private static class OrderList {
      private List<OrderItem> list;
      private String[] array;
      private String secret;
      private @Attribute int id;
      private @Element String value;
      @Transient
      public String getSecret() {
         return secret;
      }
      @Transient
      public void setSecret(String secret) {
         this.secret = secret;
      }
      @Attribute
      public String[] getArray() {
         return array;
      }
      @Attribute
      public void setArray(String[] array){
         this.array = array;
      }
      public List<OrderItem> getOrders() {
         return list;
      }
      public void setOrders(List<OrderItem> list) {
         this.list = list;
      }
   }
   
   @Root
   @Default(DefaultType.FIELD)
   private static class OrderItem {
      private static final String IGNORE = "ignore";
      private Customer customer;
      private String name;
      private int value;
      private double price;
      private @Transient String hidden;
      private @Transient String secret;
   }
   
   @Root
   @Default(DefaultType.FIELD)
   private static class Customer {
      private @Attribute int id;
      private String name;
      private String address;
      public Customer(@Element(name="name") String name) {
         this.name = name;
      }
   }
   
   @Root
   @Default(DefaultType.PROPERTY)
   private static class TypeMisMatch {
      public String name;
      public String getName() {
         return name;
      }
      @Attribute
      public void setName(String name) {
         this.name = name;
      }
   }
   /*
   public void testTypeMisMatch() throws Exception {
      Persister persister = new Persister();
      boolean failure = false;
      
      try {
         TypeMisMatch type = persister.read(TypeMisMatch.class, SOURCE);
         assertNull(type);
      }catch(Exception e){
         e.printStackTrace();
         failure = true;
      }
      assertTrue(failure);
   }
   
   public void testIgnoreStatic() throws Exception {
      Serializer serializer = new Persister();
      OrderItem order = serializer.read(OrderItem.class, ORDER);
      StringWriter writer = new StringWriter();
      
      serializer.write(order, writer);
      
      assertElementDoesNotExist(writer.toString(), "orderItem/IGNORE");
      System.out.println(writer.toString());
   }*/
   
   
   public void testDefault() throws Exception {
      MethodScanner methodScanner = new MethodScanner(new DetailScanner(OrderList.class), new Support());
      Map<String, Contact> map = new HashMap<String, Contact>();
      
      for(Contact contact : methodScanner) {
         map.put(contact.getName(), contact);
      }
      assertEquals(map.get("orders").getClass(), MethodContact.class);
      assertEquals(map.get("orders").getType(), List.class);
      assertEquals(map.get("orders").getAnnotation().annotationType(), ElementList.class);
      
      Scanner scanner = new ObjectScanner(new DetailScanner(OrderList.class), new Support());
      LabelMap attributes = scanner.getSection().getAttributes();
      LabelMap elements = scanner.getSection().getElements();  
      
      assertEquals(elements.get("orders").getType(), List.class);
      assertEquals(elements.get("orders").getContact().getAnnotation().annotationType(), ElementList.class);
      assertEquals(attributes.get("array").getType(), String[].class);
      assertEquals(attributes.get("array").getContact().getAnnotation().annotationType(), Attribute.class);
      
      Persister persister = new Persister();
      OrderList list = persister.read(OrderList.class, SOURCE);
      
      assertEquals(list.getArray()[0], "a");
      assertEquals(list.getArray()[1], "b");
      assertEquals(list.getArray()[2], "c");
      assertEquals(list.getArray()[3], "d");
      assertEquals(list.id, 100);
      assertEquals(list.value, "Some Example Value");
      assertEquals(list.getOrders().get(0).name, "IR1234");
      assertEquals(list.getOrders().get(0).hidden, null);
      assertEquals(list.getOrders().get(0).secret, null);
      assertEquals(list.getOrders().get(0).value, 2);
      assertEquals(list.getOrders().get(0).price, 7.4);
      assertEquals(list.getOrders().get(0).customer.id, 1);
      assertEquals(list.getOrders().get(0).customer.name, "John Doe");
      assertEquals(list.getOrders().get(0).customer.address, "Sin City");
      assertEquals(list.getOrders().get(1).name, "TZ346");
      assertEquals(list.getOrders().get(0).hidden, null);
      assertEquals(list.getOrders().get(0).secret, null);
      assertEquals(list.getOrders().get(1).value, 2);
      assertEquals(list.getOrders().get(1).price, 10.4);
      assertEquals(list.getOrders().get(1).customer.id, 2);
      assertEquals(list.getOrders().get(1).customer.name, "Jane Doe");
      assertEquals(list.getOrders().get(1).customer.address, "Sesame Street");
   }

}
