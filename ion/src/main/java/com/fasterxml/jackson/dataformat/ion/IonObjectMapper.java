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

package com.fasterxml.jackson.dataformat.ion;

import java.io.IOException;
import java.util.Date;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.cfg.MapperBuilderState;
import com.fasterxml.jackson.databind.deser.DefaultDeserializationContext;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.DefaultSerializerProvider;
import com.fasterxml.jackson.dataformat.ion.ionvalue.IonValueModule;

import software.amazon.ion.IonDatagram;
import software.amazon.ion.IonReader;
import software.amazon.ion.IonValue;
import software.amazon.ion.IonWriter;

/**
 * Specialization of {@link ObjectMapper} that will set underlying
 * factory to be an instance of {@link IonFactory}.
 */
public class IonObjectMapper extends ObjectMapper
{    
    private static final long serialVersionUID = 1L;

    /**
     * Base implementation for "Vanilla" {@link ObjectMapper}, used with
     * Avro backend.
     *
     * @since 3.0
     */
    public static class Builder extends MapperBuilder<IonObjectMapper, Builder>
    {
        public Builder(IonFactory f) {
            super(f);

            // 04-Jan-2017, tatu: demoted from `IonValueMapper`
            addModule(new IonValueModule());
            addModule(new EnumAsIonSymbolModule());

            // !!! 04-Jan-2018, tatu: needs to be reworked in future; may remain a module
            // Use native Ion timestamps for dates
            SimpleModule m = new SimpleModule("IonTimestampModule", PackageVersion.VERSION);
            m.addSerializer(Date.class, new IonTimestampSerializers.IonTimestampJavaDateSerializer());
            m.addSerializer(java.sql.Date.class, new IonTimestampSerializers.IonTimestampSQLDateSerializer());
            m.addDeserializer(Date.class, new IonTimestampDeserializers.IonTimestampJavaDateDeserializer());
            m.addDeserializer(java.sql.Date.class, new IonTimestampDeserializers.IonTimestampSQLDateDeserializer());
            addModule(m);
        }

        @Override
        public IonObjectMapper _constructMapper(MapperBuilderState state) {
            return new IonObjectMapper(this);
        }
    }

    /*
    /**********************************************************************
    /* Life-cycle
    /**********************************************************************
     */

    public IonObjectMapper() {
        this(new IonFactory());
    }

    public IonObjectMapper(IonFactory f) {
        this(new Builder(f));
    }

    public IonObjectMapper(Builder b) {
        super(b);
    }

    @SuppressWarnings("unchecked")
    public static Builder builder() {
        return new Builder(new IonFactory());
    }

    public static Builder builder(IonFactory streamFactory) {
        return new Builder(streamFactory);
    }

    /*
    /**********************************************************************
    /* Basic accessor overrides
    /**********************************************************************
     */

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public IonFactory tokenStreamFactory() {
        return (IonFactory) _streamFactory;
    }

    /*
    /************************************************************************
    /* Additional convenience factory methods for parsers, generators
    /************************************************************************
     */

    /**
     * @since 3.0
     */
    public IonParser createParser(IonReader src) {
        DefaultDeserializationContext ctxt = createDeserializationContext();
        return (IonParser) ctxt.assignAndReturnParser(tokenStreamFactory().createParser(ctxt, src));
    }

    /**
     * @since 3.0
     */
    public IonParser createParser(IonValue value) {
        DefaultDeserializationContext ctxt = createDeserializationContext();
        return (IonParser) ctxt.assignAndReturnParser(tokenStreamFactory().createParser(ctxt, value));
    }

    /**
     * @since 3.0
     */
    public IonGenerator createGenerator(IonWriter out) {
        return (IonGenerator) tokenStreamFactory().createGenerator(_serializerProvider(), out);
    }

    /*
    /************************************************************************
    /* Convenience read/write methods for IonReader, IonWriter, and IonValue,
    /* by analogy with the existing convenience methods of ObjectMapper
    /************************************************************************
     */

    /**
     * Deserialize an Ion value read from the supplied IonReader into a Java
     * type.
     * <p>
     * Note: method does not close the underlying reader
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonReader r, Class<T> valueType) throws IOException {
        DefaultDeserializationContext ctxt = createDeserializationContext();
        return (T)_readMapAndClose(ctxt, tokenStreamFactory().createParser(ctxt, r),
                _typeFactory.constructType(valueType));
    }

    /**
     * Deserialize an Ion value read from the supplied IonReader into a Java
     * type.
     * <p>
     * Note: method does not close the underlying reader
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> T readValue(IonReader r, TypeReference valueTypeRef) throws IOException {
        DefaultDeserializationContext ctxt = createDeserializationContext();
        return (T)_readMapAndClose(ctxt, tokenStreamFactory().createParser(ctxt, r),
                _typeFactory.constructType(valueTypeRef));
    }

    /**
     * Deserialize an Ion value read from the supplied IonReader into a Java
     * type.
     * <p>
     * Note: method does not close the underlying reader
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonReader r, JavaType valueType) throws IOException {
        DefaultDeserializationContext ctxt = createDeserializationContext();
        return (T)_readMapAndClose(ctxt, tokenStreamFactory().createParser(ctxt, r), valueType);
    }

    /**
     * Convenience method for converting Ion value into given value type.
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonValue value, Class<T> valueType) throws IOException {
        if (value == null) {
            return null;
        }
        DefaultDeserializationContext ctxt = createDeserializationContext();
        return (T)_readMapAndClose(ctxt, tokenStreamFactory().createParser(ctxt, value),
                _typeFactory.constructType(valueType));
    }

    /**
     * Convenience method for converting Ion value into given value type.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> T readValue(IonValue value, TypeReference valueTypeRef) throws IOException {
        if (value == null) {
            return null;
        }
        DefaultDeserializationContext ctxt = createDeserializationContext();
        return (T)_readMapAndClose(ctxt, tokenStreamFactory().createParser(ctxt, value),
                _typeFactory.constructType(valueTypeRef));
    }

    /**
     * Convenience method for converting Ion value into given value type.
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonValue value, JavaType valueType) throws IOException  {
        if (value == null) {
            return null;
        }
        DefaultDeserializationContext ctxt = createDeserializationContext();
        return (T)_readMapAndClose(ctxt, tokenStreamFactory().createParser(ctxt, value), valueType);
    }

    /**
     * Method that can be used to serialize any Java value as
     * Ion output, using IonWriter provided.
     *<p>
     * Note: method does not close the underlying writer explicitly
     */
    public void writeValue(IonWriter w, Object value) throws IOException {
        DefaultSerializerProvider prov = _serializerProvider();
        _configAndWriteValue(prov,
                tokenStreamFactory().createGenerator(prov, w), value);
    }

    /**
     * Method that can be used to map any Java value to an IonValue.
     */
    public IonValue writeValueAsIonValue(Object value) throws IOException
    {
        // 04-Jan-2017, tatu: Bit of incompatiblity wrt 2.x handling: should this result in
        //   Java `null`, or Ion null marker? For now, choose latter
/*        
        if (value == null) {
            return null;
        }
        */

        IonFactory f = tokenStreamFactory();
        IonDatagram container = f._system.newDatagram();
        IonWriter writer = f._system.newWriter(container);
        try {
            writeValue(writer, value);
            IonValue result = container.get(0);
            result.removeFromContainer();
            return result;
        } finally {
            writer.close();
        }
    }
}
