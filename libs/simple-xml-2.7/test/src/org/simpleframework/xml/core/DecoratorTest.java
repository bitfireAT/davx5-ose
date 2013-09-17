package org.simpleframework.xml.core;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.Text;
import org.simpleframework.xml.ValidationTestCase;
import org.simpleframework.xml.strategy.Type;
import org.simpleframework.xml.strategy.Strategy;
import org.simpleframework.xml.strategy.TreeStrategy;
import org.simpleframework.xml.strategy.Value;
import org.simpleframework.xml.stream.InputNode;
import org.simpleframework.xml.stream.NodeMap;
import org.simpleframework.xml.stream.OutputNode;

/**
 * This test is provided to demonstrate how simple it is to intercept
 * the serialization and deserialization process and manupulate the XML
 * according to requirements. It also shows how the serialized XML can
 * be written in a language neutral manner.
 * @author Niall Gallagher
 */
public class DecoratorTest extends ValidationTestCase {
    /**
     * This is used to intercept the read and write operations and 
     * change the contents of the XML elements.
     * @author Niall Gallagher
     */
    public static interface Interceptor {
        public void read(Class field, NodeMap<InputNode> node) throws Exception;
        public void write(Class field,NodeMap<OutputNode> node) throws Exception;
    }
    /**
     * This acts as a strategy and intercepts all XML elements that
     * are serialized and deserialized so that the XML can be manipulated
     * by the provided interceptor implementation.
     * @author Niall Gallagher
     */
    public static class Decorator implements Strategy{
        private final Interceptor interceptor;
        private final Strategy strategy;
        public Decorator(Interceptor interceptor, Strategy strategy){
            this.interceptor = interceptor;
            this.strategy = strategy;
        }
        /**
         * Here we intercept the call to get the element value from the
         * strategy so that we can change the attributes in the XML element
         * to match what was change on writing the element.
         * @param node this is the XML element to be modified
         */
        public Value read(Type field, NodeMap<InputNode> node, Map map) throws Exception {
            interceptor.read(field.getType(), node);            
            return strategy.read(field, node, map);
        }
        /**
         * Here we change the XML element after it has been annotated by
         * the strategy. In this way we can ensure we write what we want
         * to the resulting XML document.
         * @param node this is the XML element that will be written
         */
        public boolean write(Type field, Object value, NodeMap<OutputNode> node, Map map) throws Exception {
            boolean result = strategy.write(field, value, node, map);           
            interceptor.write(field.getType(), node);
            return result;
        }              
    }
    /**
     * The manipulator object is used to manipulate the attributes 
     * added to the XML elements by the strategy in such a way that
     * they do not contain Java class names but rather neutral ones.
     * @author Niall Gallagher
     */
    public static class Manipulator implements Interceptor {
        private final Map<String, String> read;
        private final Map<String, String> write;
        private final String label;
        private final String replace;
        private Manipulator(String label, String replace) {
            this.read = new ConcurrentHashMap<String, String>();
            this.write = new ConcurrentHashMap<String, String>();
            this.label = label;
            this.replace = replace;
        }
        /**
         * Here we are inserting an alias for a type. Each time the 
         * specified type is written the provided name is used and
         * each time the name is found on reading it is substituted 
         * for the type so that it can be interpreted correctly.
         * @param type this is the class to be given an alias
         * @param name this is the name to use
         */
        public void resolve(Class type, String value) throws Exception{
            String name = type.getName();                
            read.put(value, name);
            write.put(name, value);
        }
        public void read(Class field, NodeMap<InputNode> node) throws Exception{
            InputNode value = node.remove(replace);
            if(value != null) {
                String name = value.getValue();
                String type = read.get(name);
                if(type == null) {
                    throw new PersistenceException("Could not match name %s", name);
                }
                node.put(label, type);
            }
        }
        public void write(Class field, NodeMap<OutputNode> node) throws Exception {
            OutputNode value = node.remove(label);
            if(value != null) {
                String type = value.getValue();
                String name = write.get(type);
                if(name == null) {
                    throw new PersistenceException("Could not match class %s", type);
                }
                node.put(replace, name);
            }
        }
    }
    @Root
    public static class FriendList {
        private final @ElementList List<Friend> list;
        public FriendList(@ElementList(name="list") List<Friend> list) {
            this.list = list;
        }
        public List<Friend> getFriends() {
            return list;
        }
    }
    @Root
    public static class Friend {
        private final @Element Member member;
        private final @ElementList List<Message> messages;
        private final @Attribute Status status;
        public Friend(@Element(name="member") Member member, @ElementList(name="messages") List<Message> messages, @Attribute(name="status") Status status) {
            this.messages = messages;
            this.member = member;
            this.status = status;
        }
        public Member getMember() {
            return member;
        }
        public Status getStatus(){
            return status;
        }
        public List<Message> getMessages() {
            return messages;
        }
    }
    @Root
    public static class Member {
        private final @Element Address address;
        private final @Attribute String name;
        private final @Attribute int age;
        public Member(@Attribute(name="name") String name, @Attribute(name="age") int age, @Element(name="address") Address address) {
            this.address = address;
            this.name = name;
            this.age = age;
        }
        public boolean isPrivileged() {
            return false;
        }
        public Address getAddress() {
            return address;
        }
        public String getName(){
            return name;
        }
        public int getAge() {
            return age;
        }
    }    
    @Root
    public static class GoldMember extends Member{
        public GoldMember(@Attribute(name="name") String name, @Attribute(name="age") int age, @Element(name="address") Address address) {
            super(name, age, address);
        }
        @Override
        public boolean isPrivileged() {
            return true;
        }
    }
    @Root
    public static class Person {
        private final @Element Address address;
        private final @Attribute String name;
        private final @Attribute int age;
        public Person(@Attribute(name="name") String name, @Attribute(name="age") int age, @Element(name="address") Address address) {
            this.address = address;
            this.name = name;
            this.age = age;
        }
        public Address getAddress() {
            return address;
        }
        public String getName(){
            return name;
        }
        public int getAge() {
            return age;
        }
    }
    @Root
    public static class Address {
        private final @Element String street;
        private final @Element String city;
        private final @Element String country;
        public Address(@Element(name="street") String street, @Element(name="city") String city, @Element(name="country") String country) {
            this.street = street;
            this.city = city;
            this.country = country;
        }
        public String getStreet(){
            return street;            
        }
        public String getCity(){
            return city;
        }
        public String getCountry() {
            return country;
        }
    }
    @Root
    public static class Message {
        private String title;
        private String text;
        public Message() {
            super();
        }
        public Message(String title, String text){
            this.title = title;
            this.text = text;
        }
        @Attribute
        public void setTitle(String title) {
            this.title = title;
        }
        @Attribute
        public String getTitle() {
            return title;
        }
        @Text(data=true)
        public void setText(String text) {
            this.text = text;
        }
        @Text(data=true)
        public String getText() {
            return text;
        }
    }
    public static enum Status {
        ACTIVE,
        INACTIVE,
        DELETED
    }
    /**
     * This test will use an interceptor to replace Java class names
     * with user specified tokens so that the object can be serialized
     * and deserialized without referencing specific classes.
     */
    public void testDecorator() throws Exception {
        Strategy strategy = new TreeStrategy("class", "length");
        Manipulator manipulator = new Manipulator("class", "type");
        Decorator decorator = new Decorator(manipulator, strategy);
        Serializer serializer = new Persister(decorator);
        List<Friend> friends = new ArrayList<Friend>();
        
        Address tomAddress = new Address("14 High Steet", "London", "UK");
        Member tom = new Member("Tom", 30, tomAddress);
        List<Message> tomMessages = new ArrayList<Message>();
        
        tomMessages.add(new Message("Hello", "Hi, this is a message, Bye"));
        tomMessages.add(new Message("Hi Tom", "This is another quick message"));
        
        Address jimAddress = new Address("14 Main Road", "London", "UK");
        Member jim = new GoldMember("Jim", 30, jimAddress);
        List<Message> jimMessages = new LinkedList<Message>();
        
        jimMessages.add(new Message("Hello Jim", "Hi Jim, here is a message"));
        jimMessages.add(new Message("Hi", "Yet another message"));
        
        friends.add(new Friend(tom, tomMessages, Status.ACTIVE));
        friends.add(new Friend(jim, jimMessages, Status.INACTIVE));
        
        FriendList original = new FriendList(friends);       
        
        manipulator.resolve(ArrayList.class, "list");
        manipulator.resolve(LinkedList.class, "linked-list");
        manipulator.resolve(Member.class, "member");
        manipulator.resolve(GoldMember.class, "gold-member");
        
        StringWriter text = new StringWriter();
        serializer.write(original, text);
        String result = text.toString();
        FriendList recovered = serializer.read(FriendList.class, result);
        
        assertEquals(original.getFriends().getClass(), recovered.getFriends().getClass());
        assertEquals(original.getFriends().get(0).getStatus(), recovered.getFriends().get(0).getStatus());
        assertEquals(original.getFriends().get(0).getMember().getName(), recovered.getFriends().get(0).getMember().getName());
        assertEquals(original.getFriends().get(0).getMember().getAge(), recovered.getFriends().get(0).getMember().getAge());
        assertEquals(original.getFriends().get(0).getMember().getAddress().getCity(), recovered.getFriends().get(0).getMember().getAddress().getCity());
        assertEquals(original.getFriends().get(0).getMember().getAddress().getCountry(), recovered.getFriends().get(0).getMember().getAddress().getCountry());     
        assertEquals(original.getFriends().get(0).getMember().getAddress().getStreet(), recovered.getFriends().get(0).getMember().getAddress().getStreet());         
        assertEquals(original.getFriends().get(1).getMember().getName(), recovered.getFriends().get(1).getMember().getName());
        assertEquals(original.getFriends().get(1).getMember().getAge(), recovered.getFriends().get(1).getMember().getAge());
        assertEquals(original.getFriends().get(1).getMember().getAddress().getCity(), recovered.getFriends().get(1).getMember().getAddress().getCity());
        assertEquals(original.getFriends().get(1).getMember().getAddress().getCountry(), recovered.getFriends().get(1).getMember().getAddress().getCountry());   
        assertEquals(original.getFriends().get(1).getMember().getAddress().getStreet(), recovered.getFriends().get(1).getMember().getAddress().getStreet());  
        
        validate(serializer, original);
    }

}
