package org.simpleframework.xml.core;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;

import junit.framework.TestCase;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.Order;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.Serializer;

public class ProviderInformationTest extends TestCase  {
   
   private static final String SPDD = 
   "<?xml version='1.0' encoding='UTF-8'?>\n" +
   "<!--Sample XML file for HelloAndroid Android App deployment-->\n" +
   "<spd:SolutionPackageDeployment name='HelloAndroid'\n" +
   " uuid='34d00b69-ba29-11e0-962b-0800200c9a66'\n" +
   " deploymentOperation='BASE_INSTALL' version='1.0'\n" +
   " xsi:schemaLocation='http://www.sra.com/rtapp/spdd SolutionPackageDeploymentV0.xsd'\n" +
   " xmlns:spd='http://www.sra.com/rtapp/spdd'\n" +
   " xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance'>\n" +
   "  <spd:ShortDescription>Used for testing element name case.</spd:ShortDescription>\n" +
   "  <spd:ProviderInformation>\n" +
   "     <spd:Manufacturer name='SRA' uuid='34d00b70-ba29-11e0-962b-0800200c9a66'/>\n" +
   "  </spd:ProviderInformation>\n" +
   "</spd:SolutionPackageDeployment>\n";


   public enum DeploymentOperationType {
      BASE_INSTALL,
      CONFIGURATION,
      MAINTENANCE,
      MODIFICATION,
      REPLACEMENT,
      UNINSTALL;
      public String value() {
          return name();
      }
      public static DeploymentOperationType fromValue(String v) {
          return valueOf(v);
      }
  }
  @Root
  @Order( elements = {
      "shortDescription",
      "longDescription"
  })
  public abstract static class DescriptionType {
      @Element(name = "ShortDescription", required = true)
      protected String shortDescription;
      @Element(name = "LongDescription", required = false)
      protected String longDescription;
      public String getShortDescription() {
          return shortDescription;
      }
      public void setShortDescription(String value) {
          this.shortDescription = value;
      }
      public String getLongDescription() {
          return longDescription;
      }
      public void setLongDescription(String value) {
          this.longDescription = value;
      }
  }
  @Root
  public static class ManufacturerType {
      @Attribute(required = true)
      protected String name;
      @Attribute(required = true)
      protected String uuid;
      public String getName() {
          return name;
      }
      public void setName(String value) {
          this.name = value;
      }
      public String getUuid() {
          return uuid;
      }
      public void setUuid(String value) {
          this.uuid = value;
      }
  }
  @Root
  public static class NameDescriptionType
      extends DescriptionType
  {
      @Attribute(required = true)
      protected String name;
      public String getName() {
          return name;
      }
      public void setName(String value) {
          this.name = value;
      }
  }
  @Root
  @Order( elements = {
      "manufacturer"
  })
  public static class ProviderInformationType {
      @Element(name = "Manufacturer", required = false)
      protected ManufacturerType manufacturer;
      public ManufacturerType getManufacturer() {
          return manufacturer;
      }
      public void setManufacturer(ManufacturerType value) {
          this.manufacturer = value;
      }
  }
  public static class SolutionPackageDeploymentDescriptor
  {
     
     private SolutionPackageDeploymentType itsSolutionPackageDeployment;
     public SolutionPackageDeploymentDescriptor( InputStream aInputStream ) 
           throws Exception
     {
        Serializer serializer = new Persister();
        try
        {
           itsSolutionPackageDeployment = 
              serializer.read( SolutionPackageDeploymentType.class, aInputStream, false );
        }
        catch (Exception exception)
        {
           System.out.println( "Constructor exception " + exception.getMessage() );
           throw( exception );
        }
     }
     public SolutionPackageDeploymentDescriptor( File aFile ) throws Exception
     {
        Serializer serializer = new Persister();
        try
        {
           itsSolutionPackageDeployment = 
              serializer.read( SolutionPackageDeploymentType.class, aFile, false );
        }
        catch (Exception exception)
        {
           System.out.println( "Constructor exception " + exception.getMessage() );
           throw( exception );
        }
     }
     public SolutionPackageDeploymentDescriptor( 
           SolutionPackageDeploymentType aSolutionPackageDeploymentElement )
     {
        itsSolutionPackageDeployment = aSolutionPackageDeploymentElement;
     }
     public SolutionPackageDeploymentType getSolutionPackageDeployment()
     {
        return itsSolutionPackageDeployment;
     }
     public String toString()
     {
        Serializer serializer = new Persister();
        StringWriter xmlWriter = new StringWriter();
        try
        {
           serializer.write( itsSolutionPackageDeployment, xmlWriter );
        }
        catch (Exception exception)
        {
           final Writer result = new StringWriter(); 
           final PrintWriter printWriter = new PrintWriter(result);
           exception.printStackTrace(printWriter);
           System.out.println( "serializer.write exception " + exception.getMessage() );
           System.out.println( result.toString() );
           xmlWriter.append( "serializer.write exception " + exception.getMessage() );
        }
        return xmlWriter.toString();
     }
     public void writeToFile( File aOutputFile ) throws Exception
     {
        Serializer serializer = new Persister();
        try
        {
           serializer.write( itsSolutionPackageDeployment, aOutputFile );
        }
        catch (Exception exception)
        {
           System.out.println( "writeToFile exception " + exception.getMessage() );
           throw( exception );
        }
     }
  }
  @Root
  @Order( elements = {
      "providerInformation"
  })
  public static class SolutionPackageDeploymentType
      extends UniversallyIdentifiedType
  {
      @Element(name = "ProviderInformation", required = true)
      protected ProviderInformationType providerInformation;
      @Attribute(required = true)
      protected BigDecimal version;
      @Attribute(required = true)
      protected DeploymentOperationType deploymentOperation;
      public ProviderInformationType getProviderInformation() {
          return providerInformation;
      }
      public void setProviderInformation(ProviderInformationType value) {
          this.providerInformation = value;
      }
      public BigDecimal getVersion() {
          return version;
      }
      public void setVersion(BigDecimal value) {
          this.version = value;
      }
      public DeploymentOperationType getDeploymentOperation() {
          return deploymentOperation;
      }
      public void setDeploymentOperation(DeploymentOperationType value) {
          this.deploymentOperation = value;
      }
  }
  @Root
  public static class UniversallyIdentifiedType
      extends NameDescriptionType
  {
      @Attribute(required = true)
      protected String uuid;
      public String getUuid() {
          return uuid;
      }
      public void setUuid(String value) {
          this.uuid = value;
      }
  }

  public void testToString() throws Exception 
  {
     boolean fail = false;
     // Get the xml document to parse
     InputStream spddDocument = new ByteArrayInputStream( SPDD.getBytes("UTF-8") );
     try {
        // Create the SolutionPackageDeploymentDescriptor by parsing the file
        SolutionPackageDeploymentDescriptor spdd = 
           new SolutionPackageDeploymentDescriptor( spddDocument );
        
        // Verify that the ProviderInformation element exists
        assertNotNull( spdd.getSolutionPackageDeployment().getProviderInformation() );
        
        // Serialize the classes into a new xml document
        String serializedSPDD = new String( spdd.toString() );
        
        assertFalse( serializedSPDD.contains( "exception" ));
        
        // Write the parsed classes to the console
        System.out.println( serializedSPDD );
        
     }
     catch (Exception e)
     {
        e.printStackTrace();
        
        fail = true;
     }
     assertTrue("This test should fail because of bad order in elements", fail);
  }
  
}
