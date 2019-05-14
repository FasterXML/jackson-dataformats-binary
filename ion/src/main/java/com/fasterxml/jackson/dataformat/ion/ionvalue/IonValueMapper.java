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

import java.io.IOException;

import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;

/**
 * Supports serializing Ion to POJO and back using the Jackson Ion framework.
 *
 * Direct serialization to and from IonValue fields is supported. The POJO can declare fields of type IonValue (or a
 * subclass) and the direct value will be provided.
 * 
 * Enums are serialized as symbols by default.
 *
 * @deprecated Since 3.0: use basic {@link IonObjectMapper} with properly configured {@link IonFactory} and module(s)
 */
@Deprecated
public class IonValueMapper extends IonObjectMapper
{
    private static final long serialVersionUID = 1L;

    public IonValueMapper(IonSystem ionSystem) {
        super(IonFactory.builderForTextualWriters().ionSystem(ionSystem).build());
    }

    @Deprecated // use `readValue(IonValue, Class)` instead
    public <T> T parse(IonValue value, Class<T> clazz) throws IOException {
        if (value == null) {
            return null;
        }
        return this.readValue(value, clazz);
    }

    @Deprecated // use `writeValueAsIonValue(Object)` instead
    public IonValue serialize(Object o) throws IOException {
        if (o == null) {
            return null;
        }
        return this.writeValueAsIonValue(o);
    }
}
