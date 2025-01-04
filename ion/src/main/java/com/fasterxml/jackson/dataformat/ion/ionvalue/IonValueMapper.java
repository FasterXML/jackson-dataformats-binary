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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.dataformat.ion.EnumAsIonSymbolModule;
import com.fasterxml.jackson.dataformat.ion.IonFactory;
import com.fasterxml.jackson.dataformat.ion.IonObjectMapper;

import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.system.IonSystemBuilder;

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

    /*
    /**********************************************************************
    /* Life-cycle, constructors
    /**********************************************************************
     */

    /**
     * Constructor which provides a mapper with a null {@link PropertyNamingStrategy}.
     *
     * @param ionSystem
     */
    public IonValueMapper(IonSystem ionSystem) {
        this(ionSystem, null);
    }

    /**
     * Needed for `copy()`
     *
     * @since 2.18
     */
    protected IonValueMapper(IonValueMapper src) {
        super(src);
    }

    /**
     * Needed for some builders
     *
     * @since 2.18
     */
    protected IonValueMapper(IonFactory f, PropertyNamingStrategy strategy) {
        super(f);
        this.registerModule(new IonValueModule());
        this.registerModule(new EnumAsIonSymbolModule());
        this.setPropertyNamingStrategy(strategy);
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
        this(new IonFactory(null, ionSystem), strategy);
    }

    /*
    /**********************************************************************
    /* Life-cycle, builders
    /*
    /* NOTE: must "override" (mask) all static methods from parent class
    /* (most of which just call basic `builder()` or `builder(IonSystem)`
    /**********************************************************************
     */

    public static Builder builder() {
        return builder(IonSystemBuilder.standard().build());
    }

    public static Builder builder(IonSystem ionSystem) {
        return builder(ionSystem, null);
    }

    /**
     * Canonical {@code builder()} method that most other methods
     * ultimately call.
     */
    public static Builder builder(IonSystem ionSystem, PropertyNamingStrategy strategy) {
        return new Builder(new IonValueMapper(ionSystem, strategy));
    }

    public static Builder builderForBinaryWriters() {
        return builderForBinaryWriters(IonSystemBuilder.standard().build());
    }

    public static Builder builderForBinaryWriters(IonSystem ionSystem) {
        return builder(IonFactory.builderForBinaryWriters()
                .ionSystem(ionSystem)
                .build());
    }

    public static Builder builderForTextualWriters() {
        return builderForTextualWriters(IonSystemBuilder.standard().build());
    }

    public static Builder builderForTextualWriters(IonSystem ionSystem) {
        return builder(IonFactory.builderForTextualWriters()
                .ionSystem(ionSystem)
                .build());
    }

    public static Builder builder(IonFactory streamFactory) {
        return builder(streamFactory, null);
    }

    public static Builder builder(IonFactory streamFactory, PropertyNamingStrategy strategy) {
        return new Builder(new IonValueMapper(streamFactory, strategy));
    }

    /*
    /**********************************************************************
    /* Life-cycle, other
    /**********************************************************************
     */

    @Override // @since 2.18
    public ObjectMapper copy() {
        _checkInvalidCopy(IonValueMapper.class);
        return new IonValueMapper(this);
    }

    /*
    /**********************************************************************
    /* Extended API
    /**********************************************************************
     */
    
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
