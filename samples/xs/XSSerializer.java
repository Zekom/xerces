/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package xs;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.xerces.impl.Constants;
import org.apache.xerces.impl.dv.xs.XSSimpleTypeDecl;
import org.apache.xerces.impl.xs.XMLSchemaLoader;
import org.apache.xerces.impl.xs.XSAttributeDecl;
import org.apache.xerces.impl.xs.XSComplexTypeDecl;
import org.apache.xerces.impl.xs.XSElementDecl;
import org.apache.xerces.impl.xs.XSWildcardDecl;
import org.apache.xerces.xs.StringList;
import org.apache.xerces.xs.XSAttributeUse;
import org.apache.xerces.xs.XSComplexTypeDefinition;
import org.apache.xerces.xs.XSConstants;
import org.apache.xerces.xs.XSElementDeclaration;
import org.apache.xerces.xs.XSFacet;
import org.apache.xerces.xs.XSModel;
import org.apache.xerces.xs.XSModelGroup;
import org.apache.xerces.xs.XSMultiValueFacet;
import org.apache.xerces.xs.XSNamedMap;
import org.apache.xerces.xs.XSObject;
import org.apache.xerces.xs.XSObjectList;
import org.apache.xerces.xs.XSParticle;
import org.apache.xerces.xs.XSSimpleTypeDefinition;
import org.apache.xerces.xs.XSTerm;
import org.apache.xerces.xs.XSTypeDefinition;
import org.apache.xerces.xs.XSWildcard;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

/**
 * XSModel serialization utility.
 * This utility serializes the Xerces XSModel into lexical, XSD syntax.
 * 
 * This is a work in progress.
 * 
 * @author Mukul Gandhi, IBM
 * @version $Id$
 */
public class XSSerializer {
    
    private static final String XSD_LANGUAGE_URI = "http://www.w3.org/2001/XMLSchema";
    private static final String XSD_LANGUAGE_PREFIX = "xs:";
    private static final String XML_SCHEMA_VERSION =
                                   Constants.XERCES_PROPERTY_PREFIX + 
                                   Constants.XML_SCHEMA_VERSION_PROPERTY;
  
    /*
     * "Main method"
     * 
     * An entry point to test this utility. e.g, command line:
     * java XSSerializer -xsd11 schema.xsd
     * 
     * The XSModel could be synthesized by any means (for example, by direct
     * API calls to Xerces Schema API, methods) -- in which case, the method
     * "serialize" will be used directly, passing in the XSModel object. 
     */
    public static void main(String[] args) {
       
        if (args.length == 0 || args.length > 2) {
         System.err.println("Usage:");
         System.err.println("java XSSerializer [-xsd11] schema.xsd");
         System.err.println("-xsd11        Turn on XSD 1.1 support");
         System.exit(-1);
       }
       
       XMLSchemaLoader xsLoader = new XMLSchemaLoader();
       
       String schemaUri = "";
       if (args.length == 1) {
          schemaUri = args[0];    
       }
       else {
          schemaUri = args[1];
          if (args[0].equals("-xsd11")) {
              xsLoader.setProperty(XML_SCHEMA_VERSION,
                        Constants.W3C_XML_SCHEMA11_NS_URI);  
          }
       }
       
       XSModel xsModel = xsLoader.loadURI(schemaUri);       
       try {
          XSSerializer xsSerializer = new XSSerializer();
          xsSerializer.serialize(xsModel);
       }
       catch(Exception ex) {
         ex.printStackTrace();   
       }       
    }

    /*
     * Serialize an XML Schema, XSModel object to the standard output
     */
    public void serialize(XSModel xsModel) throws Exception {       
        //get DOM after conversion, from XSModel
       Document xsdDocument = transformXSModelToDOM(xsModel);
     
       // serialize the DOM to XML Schema document
       DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
       DOMImplementationLS impl =  (DOMImplementationLS) registry.getDOMImplementation("LS");
       LSSerializer writer = impl.createLSSerializer();
       writer.getDomConfig().setParameter("format-pretty-print", Boolean.TRUE);                  
       LSOutput output = impl.createLSOutput();
       output.setEncoding("UTF-8");
       output.setByteStream(System.out);
       writer.write(xsdDocument, output);     
    }
  
    /*
     * Transform an XML Schema, XSModel object into DOM document
     */
    public Document transformXSModelToDOM(XSModel xsModel) throws Exception {
       DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
       DocumentBuilder dBuilder = dbf.newDocumentBuilder();
       Document document = dBuilder.newDocument();
     
       Element schemaDeclDomNode = document.createElementNS(XSD_LANGUAGE_URI,
                                                            XSD_LANGUAGE_PREFIX
                                                            + "schema");
       document.appendChild(schemaDeclDomNode);
       
       // process global element declarations
       XSNamedMap globalElemDecls = xsModel.getComponents
                                            (XSConstants.ELEMENT_DECLARATION);
       processGlobalElementDecl(globalElemDecls, document, schemaDeclDomNode);
       
       // process global complex type declarations
       XSNamedMap globalComplexTypeDecls = xsModel.getComponents
                                             (XSTypeDefinition.COMPLEX_TYPE);
       processGlobalComplexTypeDecl(globalComplexTypeDecls, document, schemaDeclDomNode);
       
       // process global simple type declarations
       XSNamedMap globalSimpleTypeDecls = xsModel.getComponents
                                             (XSTypeDefinition.SIMPLE_TYPE);
       processGlobalSimpleTypeDecl(globalSimpleTypeDecls, document, schemaDeclDomNode);
       
       // process global attribute declarations
       XSNamedMap globalAttrDecls = xsModel.getComponents
                                             (XSConstants.ATTRIBUTE_DECLARATION);
       processGlobalAttrDecl(globalAttrDecls, document, schemaDeclDomNode);
     
       return document;
    } // end of, transformXSModelToDOM
    
    /*
     * Process global element declarations
     */
    private void processGlobalElementDecl(XSNamedMap globalElemDecls,
                                         Document document,
                                         Element schemaDeclDomNode)
                                         throws DOMException {
      // iterating global element declarations in the Schema
      for (int elemIdx = 0; elemIdx < globalElemDecls.size(); elemIdx++) {
         XSElementDecl elemDecl = (XSElementDecl) globalElemDecls.item(elemIdx);
         addElementDeclToSchemaComponent(document, schemaDeclDomNode,
                                         elemDecl, null, null, true);
       }
    } // end of, processGolabElementDecl

    /*
     * Adding an element declaration to Schema component
     */
    private void addElementDeclToSchemaComponent(Document document,
                                             Element parentDomNode,
                                             XSElementDecl elemDecl,
                                             String minOccurs,
                                             String maxOccurs,
                                             boolean isGlobal)
                                             throws DOMException {
         String elemName = elemDecl.getName();         
         Element elemDeclDomNode = document.createElementNS(XSD_LANGUAGE_URI,
                                                        XSD_LANGUAGE_PREFIX
                                                        + "element");
         elemDeclDomNode.setAttributeNS(null, "name", elemName); 

         XSTypeDefinition typeDef = elemDecl.getTypeDefinition();
         if (!typeDef.getAnonymous()) {
           // handling a non-anonymous schema type
           String typeName = typeDef.getName();                     
           if (XMLConstants.W3C_XML_SCHEMA_NS_URI.
                             equals(typeDef.getNamespace())) {               
               elemDeclDomNode.setAttributeNS(null, "type",
                                            XSD_LANGUAGE_PREFIX
                                            + typeName);
           }
           else {
              elemDeclDomNode.setAttributeNS(null, "type", typeName);  
           }         
         }
         else {
           // handling an anonymous schema type          
           if (typeDef.getTypeCategory() == XSTypeDefinition.SIMPLE_TYPE) {
               XSSimpleTypeDecl simpleTypeDecl = (XSSimpleTypeDecl) typeDef;
               processSimpleTypeContents(document, 
                                         elemDeclDomNode,
                                         simpleTypeDecl, 
                                         simpleTypeDecl.getName()); 
           }
           else if (typeDef.getTypeCategory() == XSTypeDefinition.COMPLEX_TYPE) {
              processAnonComplexTypeOnElement(document,
                                              elemDeclDomNode,
                                              typeDef);
           }   
         }
         
         // add possible, minOccurs & maxOccurs attributes
         if (!isGlobal) {
            if (minOccurs != null) {
               elemDeclDomNode.setAttributeNS(null, "minOccurs", minOccurs);  
            }
            if (maxOccurs != null) {
               elemDeclDomNode.setAttributeNS(null, "maxOccurs", maxOccurs);  
            }
         }

         parentDomNode.appendChild(elemDeclDomNode);
         
    } // end of, addElementDeclToSchemaComponent

    /*
     * Process global complex type declarations
     */
    private void processGlobalComplexTypeDecl(XSNamedMap globalComplexTypeDecls,
                                              Document document,
                                              Element schemaDeclDomNode)
                                              throws DOMException {
        // iterating global complex types in the Schema
        // leaving out built-in Schema type, "anyType" from iteration        
        for (int ctIdx = 0; ctIdx < globalComplexTypeDecls.size() - 1; ctIdx++) {
           XSComplexTypeDecl complexTypeDecl = (XSComplexTypeDecl) 
                                                globalComplexTypeDecls.item(ctIdx);
           String ctName = complexTypeDecl.getName();
           Element complxTypeDomNode = document.createElementNS(
                                                   XSD_LANGUAGE_URI,
                                                   XSD_LANGUAGE_PREFIX +
                                                   "complexType");
           complxTypeDomNode.setAttributeNS(null, "name", ctName);
           
           addChildrenToComplexType(document,
                                    schemaDeclDomNode,
                                    complexTypeDecl,
                                    complxTypeDomNode);
       }        
    } // end of, processGlobalComplexTypeDecl
    
    /*
     * Process global simple type declarations
     */
    private void processGlobalSimpleTypeDecl(XSNamedMap globalSimpleTypeDecls,
                                             Document document,
                                             Element schemaDeclDomNode)
                                             throws DOMException {
        // iterating global simple types in the Schema
        for (int stIdx = 0; stIdx < globalSimpleTypeDecls.size(); stIdx++) {
            XSSimpleTypeDecl simpleTypeDecl = (XSSimpleTypeDecl)
                                              globalSimpleTypeDecls.item(stIdx);
            // consider only user defined simple types
            if (!XMLConstants.W3C_XML_SCHEMA_NS_URI.equals
                                   (simpleTypeDecl.getNamespace())) {
                String stName = simpleTypeDecl.getName();
                processSimpleTypeContents(document, schemaDeclDomNode,
                                          simpleTypeDecl, stName);
            }
        }
    } // end of, processGlobalSimpleTypeDecl

    /*
     * Processing Simple Type contents
     */
    private void processSimpleTypeContents(Document document,
                                           Element parentDomNode,
                                           XSSimpleTypeDecl simpleTypeDecl,
                                           String stName)
                                           throws DOMException {
        Element simpleTypeDomNode = document.createElementNS(
                                               XSD_LANGUAGE_URI,
                                               XSD_LANGUAGE_PREFIX +
                                               "simpleType");
        if (stName != null) {
           simpleTypeDomNode.setAttributeNS(null, "name", stName);
        }
        parentDomNode.appendChild(simpleTypeDomNode);
        if (simpleTypeDecl.getVariety() == 
                        XSSimpleTypeDefinition.VARIETY_ATOMIC) {
            Element restrictionDomNode = document.createElementNS(
                                                XSD_LANGUAGE_URI,
                                                XSD_LANGUAGE_PREFIX +
                                                "restriction");
            XSTypeDefinition baseType = simpleTypeDecl.getBaseType();
            
            addRestrictionToSimpleContent(document, simpleTypeDecl,
                            simpleTypeDomNode, restrictionDomNode, baseType);
        }
        else if (simpleTypeDecl.getVariety() == 
                       XSSimpleTypeDefinition.VARIETY_LIST) {
           XSSimpleTypeDefinition listType = simpleTypeDecl.getItemType();
           addListDeclToSimpleType(document, simpleTypeDomNode, listType);
        }
        else if (simpleTypeDecl.getVariety() == 
                       XSSimpleTypeDefinition.VARIETY_UNION) {
           XSObjectList unionMemberTypes = simpleTypeDecl.getMemberTypes();           
           addUnionDeclToSimpleType(document, simpleTypeDomNode, unionMemberTypes);
        }
    } // end of, processSimpleTypeContents

    /*
     * Add children to "xs:restriction" for simple contents
     */
    private void addRestrictionToSimpleContent(Document document,
                                               XSTypeDefinition typeDefn, 
                                               Element parentDomNode,
                                               Element restrictionDomNode, 
                                               XSTypeDefinition baseType)
                                               throws DOMException {
        if (XMLConstants.W3C_XML_SCHEMA_NS_URI.
                         equals(baseType.getNamespace())) {
            restrictionDomNode.setAttributeNS(null, "base", 
                                       XSD_LANGUAGE_PREFIX + 
                                       baseType.getName());   
        }
        else {
            restrictionDomNode.setAttributeNS(null, "base",  
                                       baseType.getName());   
        }                
        
        // simple type definition to be used, to get the facets
        XSSimpleTypeDefinition simpleTypeDefn = null;
        
        if (typeDefn instanceof XSComplexTypeDefinition) {
            XSComplexTypeDefinition complexTypeDefn = (XSComplexTypeDefinition) typeDefn;
            simpleTypeDefn = complexTypeDefn.getSimpleType();
            
        }
        else {
            simpleTypeDefn = (XSSimpleTypeDefinition) typeDefn;     
        }
        
        // handling single-valued Facets
        XSObjectList facets = simpleTypeDefn.getFacets();
        for (int facetIdx = 0; facetIdx < facets.getLength(); facetIdx++) {
            XSFacet facet = (XSFacet) facets.item(facetIdx);                        
            String facetName = getFacetName(facet.getFacetKind());
            String facetValue = facet.getLexicalFacetValue();
            Element facetDomNode = document.createElementNS(
                                            XSD_LANGUAGE_URI,
                                            XSD_LANGUAGE_PREFIX +
                                            facetName);
            facetDomNode.setAttributeNS(null, "value", facetValue);
            restrictionDomNode.appendChild(facetDomNode);
        }
        
        // handling multi-valued Facets ("enumeration" or "pattern")
        XSObjectList mvFacets = simpleTypeDefn.getMultiValueFacets();
        for (int mvFacetIdx = 0; mvFacetIdx < mvFacets.getLength(); 
                                              mvFacetIdx++) {
           XSMultiValueFacet mvFacet = (XSMultiValueFacet) 
                                              mvFacets.item(mvFacetIdx);
           StringList facetValues = mvFacet.getLexicalFacetValues();
           for (int facValIdex = 0; facValIdex < facetValues.getLength(); 
                                                 facValIdex++) {
               String facetValue = (String) facetValues.get(facValIdex);
               Element facetDomNode = null;
               if (mvFacet.getFacetKind() == XSSimpleTypeDefinition.
                                             FACET_ENUMERATION) {
                   facetDomNode = document.createElementNS(
                                             XSD_LANGUAGE_URI,
                                             XSD_LANGUAGE_PREFIX +
                                             "enumeration");
               }
               else if (mvFacet.getFacetKind() == XSSimpleTypeDefinition.
                                                  FACET_PATTERN) {
                   facetDomNode = document.createElementNS(
                                             XSD_LANGUAGE_URI,
                                             XSD_LANGUAGE_PREFIX +
                                             "pattern");
               }
               facetDomNode.setAttributeNS(null, "value", facetValue);
               restrictionDomNode.appendChild(facetDomNode);
           }
        }
        
        parentDomNode.appendChild(restrictionDomNode);
        
    } // end of, addRestrictionToSimpleContent

    /*
     * Process global attribute declarations
     */
    private void processGlobalAttrDecl(XSNamedMap globalAttrDecls,
                                       Document document,
                                       Element schemaDeclDomNode)
                                       throws DOMException {
        // iterating global attribute declarations in the Schema             
        for (int attrIdx = 0; attrIdx < globalAttrDecls.size(); attrIdx++) {
           XSAttributeDecl attrDecl = (XSAttributeDecl)
                                       globalAttrDecls.item(attrIdx);
           addAttributeToSchemaComponent(document, schemaDeclDomNode, attrDecl);
         }
    } // end of, processGlobalAttrDecl

    /*
     * Add attribute declaration to a Schema component (like, xs:schema or
     * xs:complexType).
     */
    private void addAttributeToSchemaComponent(Document document,
                                               Element parentDomNode,
                                               XSAttributeDecl attrDecl)
                                               throws DOMException {
        String attrName = attrDecl.getName();            
        Element attrDeclDomNode = document.createElementNS(XSD_LANGUAGE_URI,
                                                              XSD_LANGUAGE_PREFIX
                                                              + "attribute");
        attrDeclDomNode.setAttributeNS(null, "name", attrName);        
        parentDomNode.appendChild(attrDeclDomNode);
            
        XSTypeDefinition typeDef = attrDecl.getTypeDefinition();
        if (!typeDef.getAnonymous()) {
           // handling a non-anonymous schema type
           String typeName = typeDef.getName();                     
           if (XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(typeDef.getNamespace())) {               
              attrDeclDomNode.setAttributeNS(null, "type",
                                             XSD_LANGUAGE_PREFIX
                                             + typeName);
           }
           else {
              attrDeclDomNode.setAttributeNS(null, "type", typeName);  
           }   
         }
         else {
           // handling an anonymous schema type
           XSSimpleTypeDecl simpleTypeDecl = (XSSimpleTypeDecl) typeDef;
           processSimpleTypeContents(document, 
                                     attrDeclDomNode,
                                     simpleTypeDecl, 
                                     simpleTypeDecl.getName());              
         }           
    } // end of, addAttributeToSchemaComponent

    /*
     * Processing an "anonymous" complex type declaration, on an element
     */
    private void processAnonComplexTypeOnElement(Document document,
                                                 Element elemDeclDomNode,
                                                 XSTypeDefinition typeDef)
                                                 throws DOMException {
       
       XSComplexTypeDecl complexTypeDecl = (XSComplexTypeDecl) typeDef;       
       Element complexTypeDomNode = document.createElementNS(XSD_LANGUAGE_URI,
                                                            XSD_LANGUAGE_PREFIX +
                                                            "complexType");
       addChildrenToComplexType(document,
                                elemDeclDomNode,
                                complexTypeDecl,
                                complexTypeDomNode);
       
    } // end of, processAnonComplexTypeOnElement

    /*
     * Add child content to complex type declaration
     */
    private void addChildrenToComplexType(Document document,
                                          Element parentDomNode,
                                          XSComplexTypeDecl complexTypeDecl,
                                          Element complexTypeDomNode)
                                          throws DOMException {
        
        // add "abstract" & "mixed" attributes if applicable  
        boolean isAbstract = complexTypeDecl.getAbstract();
        boolean isMixed = (complexTypeDecl.getContentType() == 
                            XSComplexTypeDefinition.CONTENTTYPE_MIXED);
        if (isAbstract) {
            complexTypeDomNode.setAttributeNS(null, "abstract", "true");   
        }
        if (isMixed) {
            complexTypeDomNode.setAttributeNS(null, "mixed", "true");   
        }
        
        // add "block" attribute if applicable
        short prohSubstitutions = complexTypeDecl.getProhibitedSubstitutions();
        String prohSubsStr = "";
        if (prohSubstitutions == (XSConstants.DERIVATION_EXTENSION | 
                                  XSConstants.DERIVATION_RESTRICTION)) {
           prohSubsStr = "#all";   
        }
        else if (prohSubstitutions == XSConstants.DERIVATION_EXTENSION) {
           prohSubsStr = "extension"; 
        }
        else if (prohSubstitutions == XSConstants.DERIVATION_RESTRICTION) {
           prohSubsStr = "restriction"; 
        }        
        if (!prohSubsStr.equals("")) {
           complexTypeDomNode.setAttributeNS(null, "block", prohSubsStr);   
        }
        
        // add "final" attribute if applicable
        short finalSet = complexTypeDecl.getFinalSet();
        String finalStr = "";
        if (finalSet == (XSConstants.DERIVATION_EXTENSION | 
                         XSConstants.DERIVATION_RESTRICTION)) {
           finalStr = "#all";   
        }
        else if (finalSet == XSConstants.DERIVATION_EXTENSION) {
           finalStr = "extension"; 
        }
        else if (finalSet == XSConstants.DERIVATION_RESTRICTION) {
           finalStr = "restriction"; 
        }        
        if (!finalStr.equals("")) {
           complexTypeDomNode.setAttributeNS(null, "final", finalStr);   
        }

        parentDomNode.appendChild(complexTypeDomNode);
        XSParticle particle = complexTypeDecl.getParticle();
        if (particle != null) {
            processParticleFromComplexType(document, complexTypeDomNode, 
                                           particle);
            // add attributes to the complex type
            addAttributesToComplexType(document, complexTypeDecl, 
                                       complexTypeDomNode);
        }
        else {
           if (complexTypeDecl.getContentType() == 
                      XSComplexTypeDefinition.CONTENTTYPE_SIMPLE) {
             // add xs:simpleContent as child of xs:complexType
             addSimpleContentToComplexType(document, complexTypeDomNode,
                                           complexTypeDecl);   
           }
           else if (complexTypeDecl.getContentType() == 
                       XSComplexTypeDefinition.CONTENTTYPE_EMPTY) {
              // add attributes to the complex type
              addAttributesToComplexType(document, complexTypeDecl, 
                                         complexTypeDomNode); 
           }
        }
                   
    } // end of, addChildrenToComplexType
    
    /*
     * Add xs:simpleContent as child of xs:complexType
     */
    private void addSimpleContentToComplexType(Document document,
                                               Element complexTypeDomNode,
                                               XSComplexTypeDecl complexTypeDecl) {
        Element simpleContentDomNode = document.createElementNS(XSD_LANGUAGE_URI,
                                                            XSD_LANGUAGE_PREFIX +
                                                            "simpleContent");
        if (complexTypeDecl.getDerivationMethod() == 
                               XSConstants.DERIVATION_RESTRICTION) {
            Element simpleContentRestrDomNode = document.createElementNS
                                                          (XSD_LANGUAGE_URI,
                                                           XSD_LANGUAGE_PREFIX +
                                                           "restriction"); 
            addAttributesToComplexType(document, complexTypeDecl, 
                                       simpleContentRestrDomNode);
            addRestrictionToSimpleContent(document,
                                          complexTypeDecl,
                                          simpleContentDomNode, 
                                          simpleContentRestrDomNode, 
                                          complexTypeDecl.getBaseType());
            simpleContentDomNode.appendChild(simpleContentRestrDomNode);
        }
        else if (complexTypeDecl.getDerivationMethod() == 
                                    XSConstants.DERIVATION_EXTENSION) {
            Element simpleContentExtDomNode = document.createElementNS
                                                        (XSD_LANGUAGE_URI,
                                                         XSD_LANGUAGE_PREFIX +
                                                         "extension");
            XSTypeDefinition baseType = complexTypeDecl.getBaseType();
            if (XMLConstants.W3C_XML_SCHEMA_NS_URI.
                              equals(baseType.getNamespace())) {
                simpleContentExtDomNode.setAttributeNS(null, "base", 
                                               XSD_LANGUAGE_PREFIX + 
                                               baseType.getName());   
            }
            else {
                simpleContentExtDomNode.setAttributeNS(null, "base",  
                                                baseType.getName());   
            }
            addAttributesToComplexType(document, complexTypeDecl, 
                                       simpleContentExtDomNode);
            simpleContentDomNode.appendChild(simpleContentExtDomNode);
        }
        
        complexTypeDomNode.appendChild(simpleContentDomNode);
        
    } // end of, addSimpleContentToComplexType 

    /*
     * Add attributes to the complex type
     */
    private void addAttributesToComplexType(Document document,
                                            XSComplexTypeDecl complexTypeDecl,
                                            Element parentDomNode)
                                            throws DOMException {
        // iterate all attributes on the Complex type.
        // all attributes on a complex type (from all of xs:attribute & xs:attributeGroup
        // declarations) are expanded, into an XSObjectList list.  
        XSObjectList attributeUses = complexTypeDecl.getAttributeUses();
        for (int attrUsesIdx = 0; attrUsesIdx < attributeUses.getLength(); attrUsesIdx++) {
           XSAttributeUse attrUse = (XSAttributeUse) attributeUses.item(attrUsesIdx);
           XSAttributeDecl attrDecl = (XSAttributeDecl) attrUse.getAttrDeclaration();
           addAttributeToSchemaComponent(document, parentDomNode, attrDecl);          
        }
    }

    /*
     * Processing a "particle" from a complex type
     */
    private void processParticleFromComplexType(Document document,
                                                Element complxTypeDomNode,
                                                XSParticle particle)
                                                throws DOMException {
        XSTerm particleTerm = particle.getTerm();
        if (particleTerm instanceof XSModelGroup) {
            XSModelGroup modelGroup = (XSModelGroup) particleTerm;
            if (modelGroup.getCompositor() == XSModelGroup.COMPOSITOR_SEQUENCE) {
                addCompositorOnComplexType(document, complxTypeDomNode,
                                           modelGroup, "sequence");
            }
            else if (modelGroup.getCompositor() == XSModelGroup.COMPOSITOR_CHOICE) {
                addCompositorOnComplexType(document, complxTypeDomNode,
                                           modelGroup, "choice");
            }
            else if (modelGroup.getCompositor() == XSModelGroup.COMPOSITOR_ALL) {
                addAllCompositorOnComplexType(document, complxTypeDomNode,
                                              modelGroup);
            }
        }        
        
    } // end of, processParticleFromComplexType

    /*
     * Adding a "sequence" or "choice" compositor on a complex type
     */
    private void addCompositorOnComplexType(Document document,
                                            Element complxTypeDomNode,
                                            XSModelGroup modelGroup,
                                            String compositor)
                                            throws DOMException {
        
        Element sequenceDeclDomNode = document.createElementNS(
                                               XSD_LANGUAGE_URI,
                                               XSD_LANGUAGE_PREFIX
                                               + compositor);
        XSObjectList compositorChildren = modelGroup.getParticles();
        for (int seqIdx = 0; seqIdx < compositorChildren.getLength(); seqIdx++) {
            XSObject seqItem = compositorChildren.item(seqIdx);
            XSParticle seqParticle = (XSParticle) seqItem;            
            String minOccurs = getMinOccursVal(seqParticle);
            String maxOccurs = getMaxOccursVal(seqParticle);
            
            XSTerm partclTerm = seqParticle.getTerm();            
            if (partclTerm instanceof XSElementDeclaration) {
               XSElementDecl elemDecl = (XSElementDecl) partclTerm;
               addElementDeclToSchemaComponent(document,
                                               sequenceDeclDomNode,
                                               elemDecl,
                                               minOccurs,
                                               maxOccurs,
                                               false);
            }
            
            // handle more compositor children like, group | choice |
            // sequence | any ... 
        }
        
        complxTypeDomNode.appendChild(sequenceDeclDomNode);
        
    } // end of, addCompositorOnComplexType
    
    /*
     * Processing an "all" declaration on a complex type
     */
    private void addAllCompositorOnComplexType(Document document,
                                               Element complxTypeDomNode,
                                               XSModelGroup modelGroup)
                                               throws DOMException {
        
        Element allDeclDomNode = document.createElementNS(
                                                 XSD_LANGUAGE_URI,
                                                 XSD_LANGUAGE_PREFIX
                                                 + "all");        
              
        XSObjectList modelParticles = modelGroup.getParticles();
        for (int prtclIdx = 0; prtclIdx < modelParticles.getLength(); 
                                                         prtclIdx++) {
            XSParticle partclItem = (XSParticle) modelParticles.item(prtclIdx);
            String minOccurs = getMinOccursVal(partclItem);
            String maxOccurs = getMaxOccursVal(partclItem);
            
            XSTerm partclTerm = partclItem.getTerm();
            if (partclTerm instanceof XSElementDeclaration) {                
               addElementDeclToSchemaComponent(document,
                                               allDeclDomNode,
                                               (XSElementDecl) partclTerm,
                                               minOccurs,
                                               maxOccurs,
                                               false);   
            }
            else if (partclTerm instanceof XSWildcard) {
               XSWildcardDecl wildCardDecl = (XSWildcardDecl) partclTerm;
               addWildcardToSchemaComponent(document,
                                            allDeclDomNode,
                                            wildCardDecl,
                                            "any");                                        
            }
        }
        
        complxTypeDomNode.appendChild(allDeclDomNode);
        
    } // end of, addAllCompositorOnComplexType 
    
    /*
     * Adding wild card to a Schema component
     */
    private void addWildcardToSchemaComponent(Document document,
                                              Element parentNode,
                                              XSWildcardDecl wildCardDecl,
                                              String wildCardType) {
        Element wildCardDomNode = document.createElementNS(XSD_LANGUAGE_URI,
                                                         XSD_LANGUAGE_PREFIX
                                                         + wildCardType);
        String processContentsVal = wildCardDecl.getProcessContentsAsString();
        if (!processContentsVal.equals("strict")) {
           wildCardDomNode.setAttributeNS(null,
                                          "processContents",
                                          processContentsVal);
        }
        parentNode.appendChild(wildCardDomNode);        
        
    } // end of, addWildcardToSchemaComponent
    
    /*
     * Add xs:list as child of xs:simpleType
     */
    private void addListDeclToSimpleType(Document document,
                                         Element simpleTypeDomNode,
                                         XSSimpleTypeDefinition listType) {
        Element listDomNode = document.createElementNS(
                                         XSD_LANGUAGE_URI,
                                         XSD_LANGUAGE_PREFIX 
                                         + "list");
        simpleTypeDomNode.appendChild(listDomNode);
        if (XMLConstants.W3C_XML_SCHEMA_NS_URI.equals
                             (listType.getNamespace())) {
            listDomNode.setAttributeNS(null, "itemType",
                                             XSD_LANGUAGE_PREFIX +
                                             listType.getName());
        }
        else {
          if (listType.getName() != null) {
             listDomNode.setAttributeNS(null, "itemType",
                                          listType.getName());
          }
          else {
            // add xs:simpleType as child of, xs:list
            XSSimpleTypeDecl simpleTypeDeclOfList = (XSSimpleTypeDecl) listType;
            processSimpleTypeContents(document, listDomNode,
                                      simpleTypeDeclOfList,
                                      simpleTypeDeclOfList.getName());
          }
        }
    } // end of, addListDeclToSimpleType
    
    /*
     * Add xs:union as child of xs:simpleType
     */
    private void addUnionDeclToSimpleType(Document document,
                                          Element simpleTypeDomNode,
                                          XSObjectList unionMemberTypes) {        
        Element unionDomNode = document.createElementNS(
                                         XSD_LANGUAGE_URI,
                                         XSD_LANGUAGE_PREFIX 
                                         + "union");
        simpleTypeDomNode.appendChild(unionDomNode);
        
        String memberTypesStr = "";
        for (int unionTypeListIdx = 0; unionTypeListIdx < unionMemberTypes.
                                                          getLength();
                                                          unionTypeListIdx++) {
           XSSimpleTypeDecl memberType = (XSSimpleTypeDecl) 
                                           unionMemberTypes.item(unionTypeListIdx);
           if (XMLConstants.W3C_XML_SCHEMA_NS_URI.equals
                                           (memberType.getNamespace())) {
               memberTypesStr = memberTypesStr + " "+ XSD_LANGUAGE_PREFIX +
                                                    memberType.getName();   
           }
           else {
             if (memberType.getName() != null) {
                memberTypesStr = memberTypesStr + " " +memberType.getName();
             }
             else {
               // add xs:simpleType as child of, xs:union
               XSSimpleTypeDecl simpleTypeDeclOfUnion = (XSSimpleTypeDecl) memberType;
               processSimpleTypeContents(document, unionDomNode,
                                         simpleTypeDeclOfUnion,
                                         simpleTypeDeclOfUnion.getName());
             }
           }          
        }
        
        if (!memberTypesStr.equals("")) {
            // discard the "blank space" character, at the beginning
            memberTypesStr = memberTypesStr.substring(1);
            unionDomNode.setAttributeNS(null,
                                        "memberTypes",
                                        memberTypesStr);   
        }
    } // end of, addUnionDeclToSimpleType
    
    /*
     * Find name of a facet given it's kind
     */
    private String getFacetName(short facetKind) {
      if (facetKind == XSSimpleTypeDefinition.FACET_MINEXCLUSIVE) {
         return "minExclusive";
      }
      else if (facetKind == XSSimpleTypeDefinition.FACET_MININCLUSIVE) {
         return "minInclusive";  
      }
      else if (facetKind == XSSimpleTypeDefinition.FACET_MAXEXCLUSIVE) {
         return "maxExclusive";  
      }
      else if (facetKind == XSSimpleTypeDefinition.FACET_MAXINCLUSIVE) {
         return "maxInclusive";  
      }
      else if (facetKind == XSSimpleTypeDefinition.FACET_TOTALDIGITS) {
         return "totalDigits";  
      }
      else if (facetKind == XSSimpleTypeDefinition.FACET_FRACTIONDIGITS) {
         return "fractionDigits";  
      }
      else if (facetKind == XSSimpleTypeDefinition.FACET_LENGTH) {
         return "length";  
      }
      else if (facetKind == XSSimpleTypeDefinition.FACET_MINLENGTH) {
         return "minLength";  
      }
      else if (facetKind == XSSimpleTypeDefinition.FACET_MAXLENGTH) {
         return "maxLength";  
      }
      else if (facetKind == XSSimpleTypeDefinition.FACET_WHITESPACE) {
         return "whiteSpace";  
      }
      
      // unreach
      return null;
    }
    
    /*
     * Given an XSD particle, get it's minOccurs value as a String.
     */
    private String getMinOccursVal(XSParticle particle) {
       String minOccursStr = null;
       
       int minOccurs = particle.getMinOccurs();        
       if (minOccurs != 1) {
          minOccursStr = String.valueOf(minOccurs);   
       } 

       return minOccursStr; 
    }
    
    /*
     * Given an XSD particle, get it's maxOccurs value as a String.
     */
    private String getMaxOccursVal(XSParticle particle) {
       String maxOccursStr = null;  
       
       int maxOccurs = particle.getMaxOccurs();
       if (particle.getMaxOccursUnbounded()) {
          maxOccursStr = "unbounded";  
       }
       else {
          if (maxOccurs != 1) {
             maxOccursStr = String.valueOf(maxOccurs);  
          }
       }

       return maxOccursStr; 
    }

}
