/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 1999-2002 The Apache Software Foundation.  
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer. 
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:  
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Xerces" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written 
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation and was
 * originally based on software copyright (c) 1999, International
 * Business Machines, Inc., http://www.apache.org.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */

package org.apache.xerces.impl.dtd;

import java.util.Hashtable;
import java.util.Enumeration;

import org.apache.xerces.impl.XMLErrorReporter;
import org.apache.xerces.impl.msg.XMLMessageFormatter;
import org.apache.xerces.impl.dv.dtd.DatatypeValidatorFactory;
import org.apache.xerces.impl.dtd.AbstractDTDGrammar;
import org.apache.xerces.impl.dtd.models.ContentModelValidator;
import org.apache.xerces.impl.dtd.XMLElementDecl;
import org.apache.xerces.impl.dtd.XMLAttributeDecl;
import org.apache.xerces.impl.dtd.XMLNotationDecl;
import org.apache.xerces.impl.dtd.XMLEntityDecl;
import org.apache.xerces.impl.dtd.XMLSimpleType;
import org.apache.xerces.impl.dtd.XMLContentSpec;
import org.apache.xerces.impl.dv.dtd.DatatypeValidator;
import org.apache.xerces.impl.dv.dtd.DatatypeValidatorFactoryImpl;
import org.apache.xerces.util.SymbolTable;

import org.apache.xerces.xni.Augmentations;
import org.apache.xerces.xni.QName;
import org.apache.xerces.xni.XMLDTDContentModelHandler;
import org.apache.xerces.xni.XMLDTDHandler;
import org.apache.xerces.xni.XMLLocator;
import org.apache.xerces.xni.XMLResourceIdentifier;
import org.apache.xerces.xni.XMLString;
import org.apache.xerces.xni.XNIException;

import org.xml.sax.SAXException;

/**
 * A DTD grammar. This class implements the XNI handler interfaces
 * for DTD information so that it can build the approprate validation
 * structures automatically from the callbacks.
 *
 * @author Eric Ye, IBM
 * @author Jeffrey Rodriguez, IBM
 * @author Andy Clark, IBM
 *
 * @version $Id$
 */
public class DTDGrammar
    extends AbstractDTDGrammar
    implements XMLDTDHandler, XMLDTDContentModelHandler {

    //
    // Constants
    //

    /** Chunk shift (8). */
    private static final int CHUNK_SHIFT = 8; // 2^8 = 256

    /** Chunk size (1 << CHUNK_SHIFT). */
    private static final int CHUNK_SIZE = (1 << CHUNK_SHIFT);

    /** Chunk mask (CHUNK_SIZE - 1). */
    private static final int CHUNK_MASK = CHUNK_SIZE - 1;

    /** Initial chunk count (1 << (10 - CHUNK_SHIFT)). */
    private static final int INITIAL_CHUNK_COUNT = (1 << (10 - CHUNK_SHIFT)); // 2^10 = 1k

    // debugging

    /** Debug DTDGrammar. */
    private static final boolean DEBUG = false;

    //
    // Data
    //

    /** Datatype validator factory. */
    protected DatatypeValidatorFactory fDatatypeValidatorFactory;

    /** Current element index. */
    protected int fCurrentElementIndex;

    /** Current attribute index. */
    protected int fCurrentAttributeIndex;

    /** fReadingExternalDTD */
    protected boolean fReadingExternalDTD = false;

    // temp variables

    /** Mixed. */
    private boolean fMixed;

    /** Element declaration. */
    private XMLElementDecl fElementDecl = new XMLElementDecl();

    /** Attribute declaration. */
    private XMLAttributeDecl fAttributeDecl = new XMLAttributeDecl();

    /** A qualified name. */
    private QName fQName = new QName();

    /** Entity declaration. */
    private XMLEntityDecl fEntityDecl = new XMLEntityDecl();

    /** Simple type. */
    private XMLSimpleType fSimpleType = new XMLSimpleType();

    /** Content spec node. */
    private XMLContentSpec fContentSpec = new XMLContentSpec();

    /** table of XMLAttributeDecl */
    Hashtable  fAttributeDeclTab   = new Hashtable();

    /** table of XMLElementDecl   */
    Hashtable   fElementDeclTab     = new Hashtable();

    /** table of XMLNotationDecl  */
    Hashtable  fNotationDeclTab    = new Hashtable();

    /** table of XMLSimplType     */
    Hashtable   fSimpleTypeTab     = new Hashtable();

    /** table of XMLEntityDecl    */
    Hashtable   fEntityDeclTab     = new Hashtable();

    /** Children content model operation stack. */
    private short[] fOpStack = null;
    
    /** Children content model index stack. */
    private int[] fNodeIndexStack = null;
    
    /** Children content model previous node index stack. */
    private int[] fPrevNodeIndexStack = null;

    /** Stack depth   */
    private int fDepth = 0;

    /** Entity stack. */
    private boolean[] fPEntityStack = new boolean[4];
    private int fPEDepth = 0;

    // additional fields(columns) for the element Decl pool in the Grammar

    /** flag if the elementDecl is External. */
    private int fElementDeclIsExternal[][] = new int[INITIAL_CHUNK_COUNT][];


    // additional fields(columns) for the attribute Decl pool in the Grammar

    /** flag if the AttributeDecl is External. */
    private int fAttributeDeclIsExternal[][] = new int[INITIAL_CHUNK_COUNT][];

    // for mixedElement method

    int valueIndex            = -1;
    int prevNodeIndex         = -1;
    int nodeIndex             = -1;

    //
    // Constructors
    //

    /** Default constructor. */
    public DTDGrammar(SymbolTable symbolTable) {
        super(symbolTable);
        setTargetNamespace("");
    } // <init>(SymbolTable)

    //
    // Public methods
    //

    /** Sets the datatype validator factory. */
    public void setDatatypeValidatorFactory(DatatypeValidatorFactory factory) {
        fDatatypeValidatorFactory = factory;
    }

    /**
     * Returns true if the specified element declaration is external.
     *
     * @param elementDeclIndex The element declaration index.
     */
    public boolean getElementDeclIsExternal(int elementDeclIndex) {

        if (elementDeclIndex < 0) {
            return false;
        }

        int chunk = elementDeclIndex >> CHUNK_SHIFT;
        int index = elementDeclIndex & CHUNK_MASK;
        return (fElementDeclIsExternal[chunk][index] != 0);

    } // getElementDeclIsExternal(int):boolean

    /**
     * Returns true if the specified attribute declaration is external.
     *
     * @param attributeDeclIndex Attribute declaration index.
     */
    public boolean getAttributeDeclIsExternal(int attributeDeclIndex) {

        if (attributeDeclIndex < 0) {
            return false;
        }

        int chunk = attributeDeclIndex >> CHUNK_SHIFT;
        int index = attributeDeclIndex & CHUNK_MASK;
        return (fAttributeDeclIsExternal[chunk][index] != 0);
    }

    public int getAttributeDeclIndex(int elementDeclIndex, String attributeDeclName) {
        if (elementDeclIndex == -1) {
            return -1;
        }
        int attDefIndex = getFirstAttributeDeclIndex(elementDeclIndex);
        while (attDefIndex != -1) {
            getAttributeDecl(attDefIndex, fAttributeDecl);

            if (fAttributeDecl.name.rawname == attributeDeclName 
                || attributeDeclName.equals(fAttributeDecl.name.rawname) ) {
                return attDefIndex;
            }
            attDefIndex = getNextAttributeDeclIndex(attDefIndex);
        }
        return -1;
    } // getAttributeDeclIndex (int,QName)

    //
    // XMLDTDHandler methods
    //

    /**
     * The start of the DTD.
     *
     * @param locator  The document locator, or null if the document
     *                 location cannot be reported during the parsing of 
     *                 the document DTD. However, it is <em>strongly</em>
     *                 recommended that a locator be supplied that can 
     *                 at least report the base system identifier of the
     *                 DTD.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startDTD(XMLLocator locator, Augmentations augs) throws XNIException {
        //Initialize stack
        fOpStack = null;
        fNodeIndexStack = null;
        fPrevNodeIndexStack = null;
    } // startDTD(XMLLocator)

    /**
     * This method notifies of the start of an entity. The DTD has the 
     * pseudo-name of "[dtd]" and parameter entity names start with '%'.
     * <p>
     * <strong>Note:</strong> Since the DTD is an entity, the handler
     * will be notified of the start of the DTD entity by calling the
     * startParameterEntity method with the entity name "[dtd]" <em>before</em> calling
     * the startDTD method.
     * 
     * @param name     The name of the parameter entity.
     * @param identifier The resource identifier.
     * @param encoding The auto-detected IANA encoding name of the entity
     *                 stream. This value will be null in those situations
     *                 where the entity encoding is not auto-detected (e.g.
     *                 internal parameter entities).
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startParameterEntity(String name, 
                                     XMLResourceIdentifier identifier,
                                     String encoding,
                                     Augmentations augs) throws XNIException {

        // keep track of this entity before fEntityDepth is increased
        if (fPEDepth == fPEntityStack.length) {
            boolean[] entityarray = new boolean[fPEntityStack.length * 2];
            System.arraycopy(fPEntityStack, 0, entityarray, 0, fPEntityStack.length);
            fPEntityStack = entityarray;
        }
        fPEntityStack[fPEDepth] = fReadingExternalDTD;
        fPEDepth++;
    
    } // startParameterEntity(String,XMLResourceIdentifier,String,Augmentations)

    /**
     * The start of the DTD external subset.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startExternalSubset(Augmentations augs) throws XNIException {
        fReadingExternalDTD = true;
    } // startExternalSubset(Augmentations)

    /**
     * This method notifies the end of an entity. The DTD has the pseudo-name
     * of "[dtd]" and parameter entity names start with '%'.
     * <p>
     * <strong>Note:</strong> Since the DTD is an entity, the handler
     * will be notified of the end of the DTD entity by calling the
     * endEntity method with the entity name "[dtd]" <em>after</em> calling
     * the endDTD method.
     * 
     * @param name The name of the entity.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endParameterEntity(String name, Augmentations augs) throws XNIException {

        fPEDepth--;
        fReadingExternalDTD = fPEntityStack[fPEDepth];

    } // endParameterEntity(String,Augmentations)

    /**
     * The end of the DTD external subset.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endExternalSubset(Augmentations augs) throws XNIException {
        fReadingExternalDTD = false;
    } // endExternalSubset(Augmentations)

    /**
     * An element declaration.
     * 
     * @param name         The name of the element.
     * @param contentModel The element content model.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void elementDecl(String name, String contentModel, Augmentations augs)
        throws XNIException {

        XMLElementDecl tmpElementDecl = (XMLElementDecl) fElementDeclTab.get(name) ;

        // check if it is already defined
        if ( tmpElementDecl != null ) {
            if (tmpElementDecl.type == -1) {
                fCurrentElementIndex = getElementDeclIndex(name, -1);
            }
            else {
                // duplicate element, ignored.
                return;
            }
        }
        else {
            fCurrentElementIndex = createElementDecl();//create element decl
        }

        XMLElementDecl elementDecl       = new XMLElementDecl();
        QName          elementName       = new QName(null, name, name, null);
        //XMLSimpleType  elementSimpleType = new XMLSimpleType();

        elementDecl.name.setValues(elementName);

        elementDecl.contentModelValidator = null;
        elementDecl.scope= -1;
        if (contentModel.equals("EMPTY")) {
            elementDecl.type = XMLElementDecl.TYPE_EMPTY;
        }
        else if (contentModel.equals("ANY")) {
            elementDecl.type = XMLElementDecl.TYPE_ANY;
        }
        else if (contentModel.startsWith("(") ) {
            if (contentModel.indexOf("#PCDATA") > 0 ) {
                elementDecl.type = XMLElementDecl.TYPE_MIXED;
            }
            else {
                elementDecl.type = XMLElementDecl.TYPE_CHILDREN;
            }
        }


        //add(or set) this elementDecl to the local cache
        this.fElementDeclTab.put(name, elementDecl );

        fElementDecl         = elementDecl; 

        if ((fDepth == 0 || 
            (fDepth == 1 && elementDecl.type == XMLElementDecl.TYPE_MIXED)) &&
            fNodeIndexStack != null) {
            if (elementDecl.type == XMLElementDecl.TYPE_MIXED) {
                int pcdata = addUniqueLeafNode(null);
                if (fNodeIndexStack[0] == -1) {
                    fNodeIndexStack[0] = pcdata;
                }
                else {
                    fNodeIndexStack[0] = addContentSpecNode(XMLContentSpec.CONTENTSPECNODE_CHOICE, 
                                                            pcdata, fNodeIndexStack[0]);
                }
            }
            setContentSpecIndex(fCurrentElementIndex, fNodeIndexStack[fDepth]);
        }

        if ( DEBUG ) {
            System.out.println(  "name = " + fElementDecl.name.localpart );
            System.out.println(  "Type = " + fElementDecl.type );
        }

        setElementDecl(fCurrentElementIndex, fElementDecl );//set internal structure

        int chunk = fCurrentElementIndex >> CHUNK_SHIFT;
        int index = fCurrentElementIndex & CHUNK_MASK;
        ensureElementDeclCapacity(chunk);
        fElementDeclIsExternal[chunk][index] = fReadingExternalDTD? 1 : 0;

    } // elementDecl(String,String)

    /**
     * An attribute declaration.
     * 
     * @param elementName   The name of the element that this attribute
     *                      is associated with.
     * @param attributeName The name of the attribute.
     * @param type          The attribute type. This value will be one of
     *                      the following: "CDATA", "ENTITY", "ENTITIES",
     *                      "ENUMERATION", "ID", "IDREF", "IDREFS", 
     *                      "NMTOKEN", "NMTOKENS", or "NOTATION".
     * @param enumeration   If the type has the value "ENUMERATION", this
     *                      array holds the allowed attribute values;
     *                      otherwise, this array is null.
     * @param defaultType   The attribute default type. This value will be
     *                      one of the following: "#FIXED", "#IMPLIED",
     *                      "#REQUIRED", or null.
     * @param defaultValue  The attribute default value, or null if no
     *                      default value is specified.
     * @param nonNormalizedDefaultValue  The attribute default value with no normalization 
     *                      performed, or null if no default value is specified.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void attributeDecl(String elementName, String attributeName, 
                              String type, String[] enumeration, 
                              String defaultType, XMLString defaultValue,
                              XMLString nonNormalizedDefaultValue, Augmentations augs) throws XNIException {

        if ( this.fElementDeclTab.containsKey( (String) elementName) ) {
            //if ElementDecl has already being created in the Grammar then remove from table, 
            //this.fElementDeclTab.remove( (String) elementName );
        }
        // then it is forward reference to a element decl, create the elementDecl first.
        else {
            fCurrentElementIndex = createElementDecl();//create element decl

            XMLElementDecl elementDecl       = new XMLElementDecl();
            elementDecl.name.setValues(null, elementName, elementName, null);
          
            elementDecl.scope= -1;
          
            //add(or set) this elementDecl to the local cache
            this.fElementDeclTab.put(elementName, elementDecl );

            //set internal structure
            setElementDecl(fCurrentElementIndex, elementDecl );
        }

        //Get Grammar index to grammar array
        int elementIndex       = getElementDeclIndex( elementName, -1 );
        
	//return, when more than one definition is provided for the same attribute of given element type
	//only the first declaration is binding and later declarations are ignored
        if (getAttributeDeclIndex(elementIndex, attributeName) != -1) {
            return;
        }

        fCurrentAttributeIndex = createAttributeDecl();// Create current Attribute Decl

        fSimpleType.clear();
        if ( defaultType != null ) {
            if ( defaultType.equals( "#FIXED") ) {
                fSimpleType.defaultType = fSimpleType.DEFAULT_TYPE_FIXED;
            } else if ( defaultType.equals( "#IMPLIED") ) {
                fSimpleType.defaultType = fSimpleType.DEFAULT_TYPE_IMPLIED;
            } else if ( defaultType.equals( "#REQUIRED") ) {
                fSimpleType.defaultType = fSimpleType.DEFAULT_TYPE_REQUIRED;
            }
        }
        if ( DEBUG ) {
            System.out.println("defaultvalue = " + defaultValue.toString() );
        }
        fSimpleType.defaultValue      = defaultValue!=null ?  defaultValue.toString() : null;
        fSimpleType.nonNormalizedDefaultValue      = nonNormalizedDefaultValue!=null ?  nonNormalizedDefaultValue.toString() : null;
        fSimpleType.enumeration       = enumeration;

        Hashtable facets = new Hashtable();
        if (type.equals("CDATA")) {
            fSimpleType.type = XMLSimpleType.TYPE_CDATA;
        }
        else if ( type.equals("ID") ) {
            fSimpleType.type = XMLSimpleType.TYPE_ID;
        }
        else if ( type.startsWith("IDREF") ) {
            fSimpleType.type = XMLSimpleType.TYPE_IDREF;
            if (type.indexOf("S") > 0) {
                fSimpleType.list = true;
            }
        }
        else if (type.equals("ENTITIES")) {
            fSimpleType.type = XMLSimpleType.TYPE_ENTITY;
            fSimpleType.list = true;
        }
        else if (type.equals("ENTITY")) {
            fSimpleType.type = XMLSimpleType.TYPE_ENTITY;
        }
        else if (type.equals("NMTOKENS")) {
            fSimpleType.type = XMLSimpleType.TYPE_NMTOKEN;
            fSimpleType.list = true;
        }
        else if (type.equals("NMTOKEN")) {
            fSimpleType.type = XMLSimpleType.TYPE_NMTOKEN;
        }
        else if (type.startsWith("NOTATION") ) {
            fSimpleType.type = XMLSimpleType.TYPE_NOTATION;
            /***
            facets.put(SchemaSymbols.ELT_ENUMERATION, fSimpleType.enumeration);
            /***/
            // REVISIT: Is this a bug? -Ac
            facets.put("enumeration", fSimpleType.enumeration);
            /***/
        }
        else if (type.startsWith("ENUMERATION") ) {
            fSimpleType.type = XMLSimpleType.TYPE_ENUMERATION;
            /***
            facets.put(SchemaSymbols.ELT_ENUMERATION, fSimpleType.enumeration);
            /***/
            facets.put("enumeration", fSimpleType.enumeration);
            /***/
        }
        else {
            // REVISIT: Report error message. -Ac
            System.err.println("!!! unknown attribute type "+type);
        }
        // REVISIT: The datatype should be stored with the attribute value
        //          and not special-cased in the XMLValidator. -Ac
        //fSimpleType.datatypeValidator = fDatatypeValidatorFactory.createDatatypeValidator(type, null, facets, fSimpleType.list);

        fQName.setValues(null, attributeName, attributeName, null);
        fAttributeDecl.setValues( fQName, fSimpleType, false );

        setAttributeDecl(elementIndex, fCurrentAttributeIndex, fAttributeDecl);

        int chunk = fCurrentAttributeIndex >> CHUNK_SHIFT;
        int index = fCurrentAttributeIndex & CHUNK_MASK;
        ensureAttributeDeclCapacity(chunk);
        fAttributeDeclIsExternal[chunk][index] = fReadingExternalDTD ?  1 : 0;

    } // attributeDecl(String,String,String,String[],String,XMLString,XMLString, Augmentations)

    /**
     * An internal entity declaration.
     * 
     * @param name The name of the entity. Parameter entity names start with
     *             '%', whereas the name of a general entity is just the 
     *             entity name.
     * @param text The value of the entity.
     * @param nonNormalizedText The non-normalized value of the entity. This
     *             value contains the same sequence of characters that was in 
     *             the internal entity declaration, without any entity
     *             references expanded.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void internalEntityDecl(String name, XMLString text,
                                   XMLString nonNormalizedText,
                                   Augmentations augs) throws XNIException {

        XMLEntityDecl  entityDecl = new XMLEntityDecl();
        boolean isPE = name.startsWith("%");
        boolean inExternal = fReadingExternalDTD;

        entityDecl.setValues(name,null,null, null, null, 
                             text.toString(), isPE, inExternal);
        int entityIndex = getEntityDeclIndex(name);
        if (entityIndex == -1) {
            entityIndex = createEntityDecl();
            setEntityDecl(entityIndex, entityDecl);
        }

    } // internalEntityDecl(String,XMLString,XMLString)

    /**
     * An external entity declaration.
     * 
     * @param name     The name of the entity. Parameter entity names start
     *                 with '%', whereas the name of a general entity is just
     *                 the entity name.
     * @param publicId The public identifier of the entity or null if the
     *                 the entity was specified with SYSTEM.
     * @param systemId The system identifier of the entity.
     * @param baseSystemId The base system identifier of the entity if
     *                     the entity is external, null otherwise.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void externalEntityDecl(String name, 
                                   String publicId, String systemId,
                                   String baseSystemId,
                                   Augmentations augs) throws XNIException {

        XMLEntityDecl  entityDecl = new XMLEntityDecl();
        boolean isPE = name.startsWith("%");
        boolean inExternal = fReadingExternalDTD;
       
        entityDecl.setValues(name, publicId, systemId, baseSystemId, 
                             null, null, isPE, inExternal);

        int entityIndex = getEntityDeclIndex(name);
        if (entityIndex == -1) {
            entityIndex = createEntityDecl();
            setEntityDecl(entityIndex, entityDecl);
        }

    } // externalEntityDecl(String,String,String)

    /**
     * An unparsed entity declaration.
     * 
     * @param name     The name of the entity.
     * @param publicId The public identifier of the entity, or null if not
     *                 specified.
     * @param systemId The system identifier of the entity, or null if not
     *                 specified.
     * @param baseSystemId	the URI of the entity that referred to this one.
     * @param notation The name of the notation.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void unparsedEntityDecl(String name, String publicId, 
                                   String systemId, String baseSystemId, String notation,
                                   Augmentations augs) throws XNIException {

        XMLEntityDecl  entityDecl = new XMLEntityDecl();
        boolean isPE = name.startsWith("%");
        boolean inExternal = fReadingExternalDTD;

        entityDecl.setValues(name,publicId,systemId, baseSystemId, notation, 
                             null, isPE, inExternal);
        int entityIndex = getEntityDeclIndex(name);
        if (entityIndex == -1) {
            entityIndex = createEntityDecl();
            setEntityDecl(entityIndex, entityDecl);
        }

    } // unparsedEntityDecl(String,String,String,String)

    /**
     * A notation declaration
     * 
     * @param name     The name of the notation.
     * @param publicId The public identifier of the notation, or null if not
     *                 specified.
     * @param systemId The system identifier of the notation, or null if not
     *                 specified.
     * @param baseSystemId	the URI of the entity that referred to this one.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void notationDecl(String name, String publicId, String systemId,
                             String baseSystemId, Augmentations augs) throws XNIException {

        XMLNotationDecl  notationDecl = new XMLNotationDecl();
        notationDecl.setValues(name,publicId,systemId, baseSystemId);
        int notationIndex = getNotationDeclIndex(name);
        if (notationIndex == -1) {
            notationIndex = createNotationDecl();
            setNotationDecl(notationIndex, notationDecl);
        }

    } // notationDecl(String,String,String)

    /**
     * The end of the DTD.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endDTD(Augmentations augs) throws XNIException {

        // REVISIT: What is this for? -Ac
        /*
        XMLElementDecl  elementDecl;
        Enumeration     elements       = fElementDeclTab.elements();
        int             elementDeclIdx = 0;
        while( elements.hasMoreElements() == true ) {
            elementDecl    = (XMLElementDecl) elements.nextElement();
            elementDeclIdx = getElementDeclIndex( elementDecl.name );
            System.out.println( "elementDeclIdx = " + elementDeclIndex );
            if( elementDeclIndex != -1 ) {   
                elementDecl.contentModelValidator = this.getElementContentModelValidator(elementDeclIdx );
            }
            fCurrentElementIndex = createElementDecl();//create element decl
            if ( DEBUG == true ) {
                System.out.println(  "name = " + fElementDecl.name.localpart );
                System.out.println(  "Type = " + fElementDecl.type );
            }
            setElementDecl(fCurrentElementIndex, fElementDecl );//set internal structure
        }
        */

    } // endDTD()

    // no-op methods

    /**
     * Notifies of the presence of a TextDecl line in an entity. If present,
     * this method will be called immediately following the startEntity call.
     * <p>
     * <strong>Note:</strong> This method is only called for external
     * parameter entities referenced in the DTD.
     * 
     * @param version  The XML version, or null if not specified.
     * @param encoding The IANA encoding name of the entity.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void textDecl(String version, String encoding, Augmentations augs) 
        throws XNIException {}

    /**
     * A comment.
     * 
     * @param text The text in the comment.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by application to signal an error.
     */
    public void comment(XMLString text, Augmentations augs) throws XNIException {}
    
    /**
     * A processing instruction. Processing instructions consist of a
     * target name and, optionally, text data. The data is only meaningful
     * to the application.
     * <p>
     * Typically, a processing instruction's data will contain a series
     * of pseudo-attributes. These pseudo-attributes follow the form of
     * element attributes but are <strong>not</strong> parsed or presented
     * to the application as anything other than text. The application is
     * responsible for parsing the data.
     * 
     * @param target The target.
     * @param data   The data or null if none specified.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void processingInstruction(String target, XMLString data,
                                      Augmentations augs) throws XNIException {}

    /**
     * The start of an attribute list.
     * 
     * @param elementName The name of the element that this attribute
     *                    list is associated with.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startAttlist(String elementName, Augmentations augs) 
        throws XNIException {}

    /**
     * The end of an attribute list.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endAttlist(Augmentations augs) throws XNIException {}

    /**
     * The start of a conditional section.
     * 
     * @param type The type of the conditional section. This value will
     *             either be CONDITIONAL_INCLUDE or CONDITIONAL_IGNORE.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     *
     * @see XMLDTDHandler#CONDITIONAL_INCLUDE
     * @see XMLDTDHandler#CONDITIONAL_IGNORE
     */
    public void startConditional(short type, Augmentations augs) 
        throws XNIException {}

    /**
     * Characters within an IGNORE conditional section.
     *
     * @param text The ignored text.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     */
    public void ignoredCharacters(XMLString text, Augmentations augs) 
        throws XNIException {}

    /**
     * The end of a conditional section.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endConditional(Augmentations augs) throws XNIException {}

    //
    // XMLDTDContentModelHandler methods
    //

    /**
     * The start of a content model. Depending on the type of the content
     * model, specific methods may be called between the call to the
     * startContentModel method and the call to the endContentModel method.
     * 
     * @param elementName The name of the element.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void startContentModel(String elementName, Augmentations augs)
        throws XNIException {
      
        XMLElementDecl elementDecl = (XMLElementDecl) this.fElementDeclTab.get( elementName);
        if ( elementDecl != null ) {
            fElementDecl = elementDecl;
        }
        fDepth = 0;
        initializeContentModelStack();

    } // startContentModel(String)

    /**
     * A start of either a mixed or children content model. A mixed
     * content model will immediately be followed by a call to the
     * <code>pcdata()</code> method. A children content model will
     * contain additional groups and/or elements.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     *
     * @see #any
     * @see #empty
     */
    public void startGroup(Augmentations augs) throws XNIException {
        fDepth++;
        initializeContentModelStack();
        fMixed = false;
    } // startGroup()

    /**
     * The appearance of "#PCDATA" within a group signifying a
     * mixed content model. This method will be the first called
     * following the content model's <code>startGroup()</code>.
     *
     *@param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     *
     * @see #startGroup
     */
    public void pcdata(Augmentations augs) throws XNIException {
        fMixed = true;
    } // pcdata()

    /**
     * A referenced element in a mixed or children content model.
     * 
     * @param elementName The name of the referenced element.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void element(String elementName, Augmentations augs) throws XNIException {
        if (fMixed) {
            if (fNodeIndexStack[fDepth] == -1 ) {
                fNodeIndexStack[fDepth] = addUniqueLeafNode(elementName);
            }
            else {
                fNodeIndexStack[fDepth] = addContentSpecNode(XMLContentSpec.CONTENTSPECNODE_CHOICE, 
                                                             fNodeIndexStack[fDepth], 
                                                             addUniqueLeafNode(elementName));
            }
        }
        else {
            fNodeIndexStack[fDepth] = addContentSpecNode(XMLContentSpec.CONTENTSPECNODE_LEAF, elementName);
        }
    } // element(String)

    /**
     * The separator between choices or sequences of a mixed or children
     * content model.
     * 
     * @param separator The type of children separator.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     *
     * @see org.apache.xerces.xni.XMLDTDContentModelHandler#SEPARATOR_CHOICE
     * @see org.apache.xerces.xni.XMLDTDContentModelHandler#SEPARATOR_SEQUENCE
     */
    public void separator(short separator, Augmentations augs) throws XNIException {

        if (!fMixed) {
            if (fOpStack[fDepth] != XMLContentSpec.CONTENTSPECNODE_SEQ && separator == XMLDTDContentModelHandler.SEPARATOR_CHOICE ) {
                if (fPrevNodeIndexStack[fDepth] != -1) {
                    fNodeIndexStack[fDepth] = addContentSpecNode(fOpStack[fDepth], fPrevNodeIndexStack[fDepth], fNodeIndexStack[fDepth]);
                }
                fPrevNodeIndexStack[fDepth] = fNodeIndexStack[fDepth];
                fOpStack[fDepth] = XMLContentSpec.CONTENTSPECNODE_CHOICE;
            } else if (fOpStack[fDepth] != XMLContentSpec.CONTENTSPECNODE_CHOICE && separator == XMLDTDContentModelHandler.SEPARATOR_SEQUENCE) {
                if (fPrevNodeIndexStack[fDepth] != -1) {
                    fNodeIndexStack[fDepth] = addContentSpecNode(fOpStack[fDepth], fPrevNodeIndexStack[fDepth], fNodeIndexStack[fDepth]);
                }
                fPrevNodeIndexStack[fDepth] = fNodeIndexStack[fDepth];
                fOpStack[fDepth] = XMLContentSpec.CONTENTSPECNODE_SEQ;
            }
        }

    } // separator(short)

    /**
     * The occurrence count for a child in a children content model or
     * for the mixed content model group.
     * 
     * @param occurrence The occurrence count for the last element
     *                   or group.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     *
     * @see org.apache.xerces.xni.XMLDTDContentModelHandler#OCCURS_ZERO_OR_ONE
     * @see org.apache.xerces.xni.XMLDTDContentModelHandler#OCCURS_ZERO_OR_MORE
     * @see org.apache.xerces.xni.XMLDTDContentModelHandler#OCCURS_ONE_OR_MORE
     */
    public void occurrence(short occurrence, Augmentations augs) throws XNIException {

        if (!fMixed) {
            if (occurrence == XMLDTDContentModelHandler.OCCURS_ZERO_OR_ONE ) {
                fNodeIndexStack[fDepth] = addContentSpecNode(XMLContentSpec.CONTENTSPECNODE_ZERO_OR_ONE, fNodeIndexStack[fDepth], -1);
            } else if ( occurrence == XMLDTDContentModelHandler.OCCURS_ZERO_OR_MORE ) {
                fNodeIndexStack[fDepth] = addContentSpecNode(XMLContentSpec.CONTENTSPECNODE_ZERO_OR_MORE, fNodeIndexStack[fDepth], -1 );
            } else if ( occurrence == XMLDTDContentModelHandler.OCCURS_ONE_OR_MORE) {
                fNodeIndexStack[fDepth] = addContentSpecNode(XMLContentSpec.CONTENTSPECNODE_ONE_OR_MORE, fNodeIndexStack[fDepth], -1 );
            }
        }

    } // occurrence(short)

    /**
     * The end of a group for mixed or children content models.
     * 
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endGroup(Augmentations augs) throws XNIException {

        if (!fMixed) {
            if (fPrevNodeIndexStack[fDepth] != -1) {
                fNodeIndexStack[fDepth] = addContentSpecNode(fOpStack[fDepth], fPrevNodeIndexStack[fDepth], fNodeIndexStack[fDepth]);
            }
            int nodeIndex = fNodeIndexStack[fDepth--];
            fNodeIndexStack[fDepth] = nodeIndex;
        }

    } // endGroup()

    // no-op methods

    /** 
     * A content model of ANY. 
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     *
     * @see #empty
     * @see #startGroup
     */
    public void any(Augmentations augs) throws XNIException {}

    /**
     * A content model of EMPTY.
     *
     * @param augs Additional information that may include infoset
     *                      augmentations.
     * @throws XNIException Thrown by handler to signal an error.
     *
     * @see #any
     * @see #startGroup
     */
    public void empty(Augmentations augs) throws XNIException {}

    /**
     * The end of a content model.
     * @param augs Additional information that may include infoset
     *                      augmentations.
     *
     * @throws XNIException Thrown by handler to signal an error.
     */
    public void endContentModel(Augmentations augs) throws XNIException {}

    //
    // Grammar methods
    //

    /** Returns true if this grammar is namespace aware. */
    public boolean isNamespaceAware() {
        return false;
    } // isNamespaceAware():boolean

    /** Returns the element decl index. 
     * @param elementDeclQName qualilfied name of the element
     * @param scope 
     */
    public int getElementDeclIndex(QName elementDeclQName, int scope) {
        return getElementDeclIndex(elementDeclQName.rawname, scope);
    } // getElementDeclIndex(QName,int):int
   
    //
    // Protected methods
    //

    /**
     * Create an XMLContentSpec for a single non-leaf
     * 
     * @param nodeType the type of XMLContentSpec to create - from XMLContentSpec.CONTENTSPECNODE_*
     * @param nodeValue handle to an XMLContentSpec
     * @return handle to the newly create XMLContentSpec
     */
    protected int addContentSpecNode(short nodeType, String nodeValue) {

        // create content spec node
        int contentSpecIndex = createContentSpec();

        // set content spec node values
        fContentSpec.setValues(nodeType, nodeValue, null);
        setContentSpec(contentSpecIndex, fContentSpec);

        // return index 
        return contentSpecIndex;

    } // addContentSpecNode(short,String):int

    /**
     * create an XMLContentSpec for a leaf
     *
     * @param   elementName  the name (Element) for the node
     * @return handle to the newly create XMLContentSpec
     */
    protected int addUniqueLeafNode(String elementName) {

        // create content spec node
        int contentSpecIndex = createContentSpec();

        // set content spec node values
        fContentSpec.setValues( XMLContentSpec.CONTENTSPECNODE_LEAF, 
                                elementName, null);
        setContentSpec(contentSpecIndex, fContentSpec);

        // return index 
        return contentSpecIndex;

    } // addUniqueLeafNode(String):int

    /**
     * Create an XMLContentSpec for a two child leaf
     *
     * @param nodeType the type of XMLContentSpec to create - from XMLContentSpec.CONTENTSPECNODE_*
     * @param leftNodeIndex handle to an XMLContentSpec
     * @param rightNodeIndex handle to an XMLContentSpec
     * @return handle to the newly create XMLContentSpec
     */
    protected int addContentSpecNode(short nodeType, 
                                     int leftNodeIndex, int rightNodeIndex) {

        // create content spec node
        int contentSpecIndex = createContentSpec();

        // set content spec node values
        int[] leftIntArray  = new int[1]; 
        int[] rightIntArray = new int[1];

        leftIntArray[0]      = leftNodeIndex;
        rightIntArray[0]    = rightNodeIndex;
        fContentSpec.setValues(nodeType, leftIntArray, rightIntArray);
        setContentSpec(contentSpecIndex, fContentSpec);

        // return index 
        return contentSpecIndex;

    } // addContentSpecNode(short,int,int):int

    /** Initialize content model stack. */
    protected void initializeContentModelStack() {

        if (fOpStack == null) {
            fOpStack = new short[8];
            fNodeIndexStack = new int[8];
            fPrevNodeIndexStack = new int[8];
        } else if (fDepth == fOpStack.length) {
            short[] newStack = new short[fDepth * 2];
            System.arraycopy(fOpStack, 0, newStack, 0, fDepth);
            fOpStack = newStack;
            int[]   newIntStack = new int[fDepth * 2];
            System.arraycopy(fNodeIndexStack, 0, newIntStack, 0, fDepth);
            fNodeIndexStack = newIntStack;
            newIntStack = new int[fDepth * 2];
            System.arraycopy(fPrevNodeIndexStack, 0, newIntStack, 0, fDepth);
            fPrevNodeIndexStack = newIntStack;
        }
        fOpStack[fDepth] = -1;
        fNodeIndexStack[fDepth] = -1;
        fPrevNodeIndexStack[fDepth] = -1;

    } // initializeContentModelStack()

    
    /** Ensures storage for element declaration mappings. */
    protected boolean ensureElementDeclCapacity(int chunk) {
        try {
            return fElementDeclIsExternal[chunk][0] == 0;
        } catch (ArrayIndexOutOfBoundsException ex) {
            fElementDeclIsExternal = resize(fElementDeclIsExternal, 
                                     fElementDeclIsExternal.length * 2);
        } catch (NullPointerException ex) {
            // ignore
        }
        fElementDeclIsExternal[chunk] = new int[CHUNK_SIZE];
        return true;
    }

    /** Ensures storage for attribute declaration mappings. */
    protected boolean ensureAttributeDeclCapacity(int chunk) {
        try {
            return fAttributeDeclIsExternal[chunk][0] == 0;
        } catch (ArrayIndexOutOfBoundsException ex) {
            fAttributeDeclIsExternal = resize(fAttributeDeclIsExternal, 
                                       fAttributeDeclIsExternal.length * 2);
        } catch (NullPointerException ex) {
            // ignore
        }
        fAttributeDeclIsExternal[chunk] = new int[CHUNK_SIZE];
        return true;
    }

    /** Resizes chunked integer arrays. */
    protected int[][] resize(int array[][], int newsize) {
        int newarray[][] = new int[newsize][];
        System.arraycopy(array, 0, newarray, 0, array.length);
        return newarray;
    }

} // class DTDGrammar
