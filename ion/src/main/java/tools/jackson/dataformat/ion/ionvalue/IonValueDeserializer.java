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

package tools.jackson.dataformat.ion.ionvalue;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.util.AccessPattern;
import tools.jackson.dataformat.ion.IonParser;

import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.Timestamp;

/**
 * Deserializer that knows how to deserialize an IonValue.
 */
class IonValueDeserializer extends ValueDeserializer<IonValue>
{
    @Override
    public IonValue deserialize(JsonParser jp, DeserializationContext ctxt)
        throws JacksonException
    {
        Object embeddedObject = jp.getEmbeddedObject();
        if (embeddedObject instanceof IonValue) {
            return (IonValue) embeddedObject;
        }
        // We rely on the IonParser's IonSystem to wrap supported types into an IonValue
        if (!(jp instanceof IonParser)) {
            throw DatabindException.from(jp, "Unsupported parser for deserializing "
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
        throw DatabindException.from(jp, "Cannot deserialize embedded object type "
                + embeddedObject.getClass().getCanonicalName() + " into IonValue");
    }

    @Override
    public Object getNullValue(DeserializationContext ctxt) throws JacksonException {
        final JsonParser parser = ctxt.getParser();
        if (parser != null && parser.currentToken() != JsonToken.END_OBJECT) {
            final Object embeddedObj = parser.getEmbeddedObject();
            if (embeddedObj instanceof IonValue) {
                IonValue iv = (IonValue) embeddedObj;
                if (iv.isNullValue()) {
                    return iv;
                }
            }
        }
        return super.getNullValue(ctxt);
    }

    @Override
    public AccessPattern getNullAccessPattern() {
        return AccessPattern.DYNAMIC;
    }
}
