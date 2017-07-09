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

import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.dataformat.ion.EnumAsIonSymbolModule;
import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import software.amazon.ion.IonSystem;
import software.amazon.ion.IonValue;

/**
 * Supports serializing Ion to POJO and back using the Jackson Ion framework.
 *
 * Direct serialization to and from IonValue fields is supported. The POJO can declare fields of type IonValue (or a
 * subclass) and the direct value will be provided.
 * 
 * Enums are serialized as symbols by default.
 */
public class IonValueMapper extends IonObjectMapper
{
    private static final long serialVersionUID = 1L;

    /**
     * Constructor which provides a mapper with a null {@link PropertyNamingStrategy}.
     *
     * @param ionSystem
     */
    public IonValueMapper(IonSystem ionSystem) {
        this(ionSystem, null);
    }

    /**
     * Constructor that provides an override on the default Constructor for the PropertyNamingStrategy.
     *
     * @param ionSystem
     * @param strategy
     *            Strategy Jackson uses for mapping POJO field names to Json/Ion field names
     *            {@link PropertyNamingStrategy}
     */
    public IonValueMapper(IonSystem ionSystem, PropertyNamingStrategy strategy) {
        super(new IonFactory(null, ionSystem));
        this.registerModule(new IonValueModule());
        this.registerModule(new EnumAsIonSymbolModule());
        this.setPropertyNamingStrategy(strategy);
    }

    public <T> T parse(IonValue value, Class<T> clazz) throws IOException {
        if (value == null) {
            return null;
        }

        return this.readValue(value, clazz);
    }

    public <T> IonValue serialize(T o) throws IOException {
        if (o == null) {
            return null;
        }

        return this.writeValueAsIonValue(o);
    }
}
