/*
 * The Apache Software License, Version 1.1
 *
 *
 * Copyright (c) 1999,2000 The Apache Software Foundation.  All rights 
 * reserved.
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

package org.apache.xerces.framework;

import java.util.Hashtable;
import java.util.Locale;
import org.apache.xerces.utils.MessageFormatter;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

/**
 * @author Stubs generated by DesignDoc on Mon Sep 11 11:10:57 PDT 2000
 * @author Eric Ye, IBM
 * @version $Id$
 */
public class XMLErrorReporter
    implements XMLComponent {

    //
    // Constants
    //

    /** SEVERITY_WARNING */
    public static final short SEVERITY_WARNING = -1;

    /** SEVERITY_ERROR */
    public static final short SEVERITY_ERROR = -1;

    /** SEVERITY_FATAL_ERROR */
    public static final short SEVERITY_FATAL_ERROR = -1;
    
    
    /** Xerces features prefix. */
    protected static final String XERCES_FEATURES_PREFIX =
        "http://apache.org/xml/features/";

    /** Xerces properties prefix. */
    protected static final String XERCES_PROPERTIES_PREFIX =
        "http://apache.org/xml/properties/";

    //
    // Data
    //

    /** fLocale */
    protected Locale fLocale;

    /** fMessageFormatters */
    protected Hashtable fMessageFormatters;

    /** fErrorHandler */
    protected ErrorHandler fErrorHandler;

    /** fLocator */
    protected Locator fLocator;

    /** fContinueAfterFatalError */
    protected boolean fContinueAfterFatalError;

    //
    // Constructors
    //

    /**
     * 
     */
    public XMLErrorReporter() {
        fMessageFormatters = new Hashtable();
    }

    //
    // Methods
    //

    /**
     * setLocale
     * 
     * @param locale 
     */
    public void setLocale(Locale locale) {
        fLocale = locale;
    } // setLocale

    /**
     * putMessageFormatter
     * 
     * @param domain 
     * @param messageFormatter 
     */
    public void putMessageFormatter(String domain, MessageFormatter messageFormatter) {
        fMessageFormatters.put(domain, messageFormatter);
    } // putMessageFormatter

    /**
     * getMessageFormatter
     * 
     * @param domain 
     * 
     * @return 
     */
    public MessageFormatter getMessageFormatter(String domain) {
        return (MessageFormatter) fMessageFormatters.get(domain);
    } // getMessageFormatter

    /**
     * removeMessageFormatter
     * 
     * @param domain 
     * 
     * @return 
     */
    public MessageFormatter removeMessageFormatter(String domain) {
        return (MessageFormatter) fMessageFormatters.remove(domain);
    } // removeMessageFormatter

    /**
     * reportError
     * 
     * @param domain 
     * @param key 
     * @param arguments 
     * @param severity 
     */
    public void reportError(String domain, String key, Object[] arguments, short severity)
        throws SAXException {

        SAXParseException spe;

        MessageFormatter msgFormatter = (MessageFormatter) fMessageFormatters.get(domain);

        spe = new SAXParseException(msgFormatter.formatMessage(fLocale, key, arguments), fLocator);

        // default error handling   
        if (fErrorHandler == null) {
            if ( severity == SEVERITY_FATAL_ERROR 
                 && !fContinueAfterFatalError) {
                throw spe;
            }
            return;
        }

        // call ErrorHandler callbacks
        if (severity == SEVERITY_WARNING ) {
            fErrorHandler.warning(spe);
        }
        else if (severity == SEVERITY_FATAL_ERROR) {
            fErrorHandler.fatalError(spe);
            if (!fContinueAfterFatalError) {
                //
                // !! in Xerces 1, spe was wrapped again.
                //
                throw spe;
            }
        }
        else {
            fErrorHandler.error(spe);
        }

    } // reportError

    //
    // XMLComponent methods
    //

    /**
     * reset
     * 
     * @param configurationManager 
     */
    public void reset(XMLComponentManager configurationManager)
        throws SAXException {
    } // reset

    /**
     * setFeature
     * 
     * @param featureId 
     * @param state 
     */
    public void setFeature(String featureId, boolean state)
        throws SAXNotRecognizedException, SAXNotSupportedException {
        if (featureId.startsWith(XERCES_FEATURES_PREFIX)) {
            String feature =
                featureId.substring(XERCES_FEATURES_PREFIX.length());
            //
            // http://apache.org/xml/features/continue-after-fatal-error
            //   Allows the parser to continue after a fatal error.
            //   Normally, a fatal error would stop the parse.
            //
            if (feature.equals("continue-after-fatal-error")) {
                fContinueAfterFatalError = state;
            }
        }
    } // setFeature

    /**
     * setProperty
     * 
     * @param propertyId 
     * @param value 
     */
    public void setProperty(String propertyId, Object value)
        throws SAXNotRecognizedException, SAXNotSupportedException {

        if (propertyId.startsWith(XERCES_PROPERTIES_PREFIX)) {
            String property =
                propertyId.substring(XERCES_PROPERTIES_PREFIX.length());

            if (property.equals("internal/locator")) {
                fLocator = (Locator) value;
            }
            else if (property.equals("internal/error-handler")) {
                fErrorHandler = (ErrorHandler) value;
            }
        }
    } // setProperty

} // class XMLErrorReporter
