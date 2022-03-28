/*
 * Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at:
 *
 *     http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
 * language governing permissions and limitations under the License.
 */

package com.fasterxml.jackson.dataformat.ion.ionvalue;

import com.amazon.ion.IonNull;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.util.AccessPattern;
import com.fasterxml.jackson.dataformat.ion.IonParser;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.Timestamp;

/**
 * Deserializer that knows how to deserialize an IonValue.
 */
class IonValueDeserializer extends JsonDeserializer<IonValue> {

    @Override
    public IonValue deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        Object embeddedObject = jp.getEmbeddedObject();
        if (embeddedObject instanceof IonValue) {
            return (IonValue) embeddedObject;
        }
        // We rely on the IonParser's IonSystem to wrap supported types into an IonValue
        if (!(jp instanceof IonParser)) {
            throw JsonMappingException.from(jp, "Unsupported parser for deserializing "
                    + embeddedObject.getClass().getCanonicalName() + " into IonValue");
        }

        IonSystem ionSystem = ((IonParser) jp).getIonSystem();
        if (embeddedObject instanceof Timestamp) {
            return ionSystem.newTimestamp((Timestamp) embeddedObject);
        }
        if (embeddedObject instanceof byte[]) {
            // The parser provides no distinction between BLOB and CLOB, deserializing to a BLOB is the safest choice.
            return ionSystem.newBlob((byte[]) embeddedObject);
        }
        throw JsonMappingException.from(jp, "Cannot deserialize embedded object type "
                + embeddedObject.getClass().getCanonicalName() + " into IonValue");
    }

    @Override
    public IonValue getNullValue(DeserializationContext ctxt) throws JsonMappingException {
        try {
            final JsonParser parser = ctxt.getParser();
            if (parser != null && parser.getCurrentToken() != JsonToken.END_OBJECT) {
                final Object embeddedObj = parser.getEmbeddedObject();
                if ((embeddedObj instanceof IonValue) && !(embeddedObj instanceof IonNull)) {
                    final IonValue iv = (IonValue) embeddedObj;
                    if (iv.isNullValue()) {
                        return iv;
                    }
                }
            }

            return super.getNullValue(ctxt);
        } catch (IOException e) {
            throw JsonMappingException.from(ctxt, e.toString());
        }
    }

    @Override
    public AccessPattern getNullAccessPattern() {
        return AccessPattern.DYNAMIC;
    }
}
