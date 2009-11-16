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

package org.apache.xerces.impl.dv.xs;

import org.apache.xerces.impl.dv.InvalidDatatypeValueException;
import org.apache.xerces.impl.dv.ValidationContext;

/**
 * Represents the schema type "error"
 * 
 * @xerces.internal 
 *
 * @author Mukul Gandhi, IBM
 * 
 * @version $Id$
 */
public class ErrorDV extends TypeValidator {

    public short getAllowedFacets() {
        return XSSimpleTypeDecl.FACET_NONE;
    }

    public Object getActualValue(String content, ValidationContext context) throws InvalidDatatypeValueException {
        throw new InvalidDatatypeValueException("cvc-datatype-valid.1.2.1", new Object[]{content, "error"});
    }

} // class ErrorDV
