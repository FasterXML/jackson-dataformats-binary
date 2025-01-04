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
import com.fasterxml.jackson.databind.module.SimpleModule;

import com.amazon.ion.IonDatagram;
import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonSystemBuilder;

/**
 * Specialization of {@link ObjectMapper} that will set underlying
 * {@link com.fasterxml.jackson.core.JsonFactory}
 * to be an instance of {@link IonFactory}.
 */
public class IonObjectMapper extends ObjectMapper
{
    private static final long serialVersionUID = 1L;

    /**
     * Base implementation for "Vanilla" {@link ObjectMapper}, used with
     * Ion backend.
     *
     * @since 2.10
     */
    public static class Builder extends MapperBuilder<IonObjectMapper, Builder>
    {
        protected final IonFactory _streamFactory;

        public Builder(IonObjectMapper m) {
            super(m);
            _streamFactory = m.getFactory();
        }

        public Builder enable(IonParser.Feature... features) {
            for (IonParser.Feature feature : features) {
                _streamFactory.enable(feature);
            }
            return this;
        }

        public Builder disable(IonParser.Feature... features) {
            for (IonParser.Feature feature : features) {
                _streamFactory.disable(feature);
            }
            return this;
        }

        public Builder configure(IonParser.Feature feature, boolean state) {
            if (state) {
                _streamFactory.enable(feature);
            } else {
                _streamFactory.disable(feature);
            }
            return this;
        }

        public Builder enable(IonGenerator.Feature... features) {
            for (IonGenerator.Feature feature : features) {
                _streamFactory.enable(feature);
            }
            return this;
        }

        public Builder disable(IonGenerator.Feature... features) {
            for (IonGenerator.Feature feature : features) {
                _streamFactory.disable(feature);
            }
            return this;
        }

        public Builder configure(IonGenerator.Feature feature, boolean state) {
            if (state) {
                _streamFactory.enable(feature);
            } else {
                _streamFactory.disable(feature);
            }
            return this;
        }
    }

    /*
    /**********************************************************************
    /* Life-cycle, constructors
    /**********************************************************************
     */

    /**
     * Constructor that will construct the mapper with a standard {@link IonFactory}
     * as codec, using textual writers by default.
     */
    public IonObjectMapper() {
        this(new IonFactory());
    }

    /**
     * Constructor that will construct the mapper with a given {@link IonFactory}.
     */
    public IonObjectMapper(IonFactory f) {
        super(f);
        f.setCodec(this);
        // Use native Ion timestamps for dates
        SimpleModule m = new SimpleModule("IonTimestampModule", PackageVersion.VERSION);
        m.addSerializer(Date.class, new IonTimestampSerializers.IonTimestampJavaDateSerializer());
        m.addSerializer(java.sql.Date.class, new IonTimestampSerializers.IonTimestampSQLDateSerializer());
        m.addDeserializer(Date.class, new IonTimestampDeserializers.IonTimestampJavaDateDeserializer());
        m.addDeserializer(java.sql.Date.class, new IonTimestampDeserializers.IonTimestampSQLDateDeserializer());
        registerModule(m);
    }

    protected IonObjectMapper(IonObjectMapper src) {
        super(src);
    }

    /*
    /**********************************************************************
    /* Life-cycle, builders
    /**********************************************************************
     */

    /**
     * A builder for a mapper that will use textual writers by default. Same as
     * {@link #builderForTextualWriters()}.
     */
    public static Builder builder() {
        return builderForTextualWriters();
    }

    /**
     * A builder for a mapper that will use textual writers by default and the
     * provided {@link IonSystem}. Same as {@link #builderForTextualWriters(IonSystem)}.
     */
    public static Builder builder(IonSystem ionSystem) {
        return builderForTextualWriters(ionSystem);
    }

    /**
     * A builder for a mapper that will use binary writers by default.
     */
    public static Builder builderForBinaryWriters() {
        return builderForBinaryWriters(IonSystemBuilder.standard().build());
    }

    /**
     * A builder for a mapper that will use binary writers by default and the
     * provided {@link IonSystem}
     */
    public static Builder builderForBinaryWriters(IonSystem ionSystem) {
        return builder(IonFactory.builderForBinaryWriters()
                .ionSystem(ionSystem)
                .build());
    }

    /**
     * A builder for a mapper that will use textual writers by default.
     */
    public static Builder builderForTextualWriters() {
        return builderForTextualWriters(IonSystemBuilder.standard().build());
    }

    /**
     * A builder for a mapper that will use textual writers by default and the
     * provided {@link IonSystem}.
     */
    public static Builder builderForTextualWriters(IonSystem ionSystem) {
        return builder(IonFactory.builderForTextualWriters()
                .ionSystem(ionSystem)
                .build());
    }

    public static Builder builder(IonFactory streamFactory) {
        return new Builder(new IonObjectMapper(streamFactory));
    }

    /*
    /**********************************************************************
    /* Life-cycle, other
    /**********************************************************************
     */

    @Override
    public ObjectMapper copy() {
        _checkInvalidCopy(IonObjectMapper.class);
        return new IonObjectMapper(this);
    }

    public void setCreateBinaryWriters(boolean bin) {
        getFactory().setCreateBinaryWriters(bin);
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    /*
    /**********************************************************************
    /* Configuration
    /**********************************************************************
     */

    public IonObjectMapper configure(IonGenerator.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    public IonObjectMapper configure(IonParser.Feature f, boolean state) {
        return state ? enable(f) : disable(f);
    }

    public IonObjectMapper enable(IonGenerator.Feature f) {
        ((IonFactory)_jsonFactory).enable(f);
        return this;
    }

    public IonObjectMapper enable(IonParser.Feature f) {
        ((IonFactory)_jsonFactory).enable(f);
        return this;
    }

    public IonObjectMapper disable(IonGenerator.Feature f) {
        ((IonFactory)_jsonFactory).disable(f);
        return this;
    }

    public IonObjectMapper disable(IonParser.Feature f) {
        ((IonFactory)_jsonFactory).disable(f);
        return this;
    }

    @Override
    public IonFactory getFactory() {
        return (IonFactory) _jsonFactory;
    }

    /*
     ************************************************************************
     * Convenience factory methods added in 2.12 (similar to ones added in
     * core in 2.11)
     ************************************************************************
     */

    /**
     * @since 2.12
     */
    public IonParser createParser(IonReader reader) throws IOException {
        _assertNotNull("value", reader);
        IonParser p = getFactory().createParser(reader);
        _deserializationConfig.initialize(p);
        return p;
    }

    /**
     * @since 2.12
     */
    public IonParser createParser(IonValue value) throws IOException {
        _assertNotNull("value", value);
        IonParser p = getFactory().createParser(value);
        _deserializationConfig.initialize(p);
        return p;
    }

    /**
     * @since 2.12
     */
    public IonGenerator createGenerator(IonWriter writer) throws IOException {
        _assertNotNull("writer", writer);
        IonGenerator g = (IonGenerator) getFactory().createGenerator(writer);
        _serializationConfig.initialize(g);
        return g;
    }

    /*
     ************************************************************************
     * Convenience read/write methods for IonReader, IonWriter, and IonValue,
     * by analogy with the existing convenience methods of ObjectMapper
     ************************************************************************
     */

    /**
     * Deserialize an Ion value read from the supplied IonReader into a Java
     * type.
     * <p>
     * Note: method does not close the underlying reader
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonReader r, Class<T> valueType) throws IOException {
        return (T)_readMapAndClose(createParser(r), _typeFactory.constructType(valueType));
    }

    /**
     * Deserialize an Ion value read from the supplied IonReader into a Java
     * type.
     * <p>
     * Note: method does not close the underlying reader
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> T readValue(IonReader r, TypeReference valueTypeRef) throws IOException {
        return (T)_readMapAndClose(createParser(r), _typeFactory.constructType(valueTypeRef));
    }

    /**
     * Deserialize an Ion value read from the supplied IonReader into a Java
     * type.
     * <p>
     * Note: method does not close the underlying reader
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonReader r, JavaType valueType) throws IOException {
        return (T)_readMapAndClose(createParser(r), valueType);
    }

    /**
     * Convenience method for converting Ion value into given value type.
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonValue value, Class<T> valueType) throws IOException {
        return (T)_readMapAndClose(createParser(value), _typeFactory.constructType(valueType));
    }

    /**
     * Convenience method for converting Ion value into given value type.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> T readValue(IonValue value, TypeReference valueTypeRef) throws IOException {
        return (T)_readMapAndClose(createParser(value), _typeFactory.constructType(valueTypeRef));
    }

    /**
     * Convenience method for converting Ion value into given value type.
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonValue value, JavaType valueType) throws IOException  {
        return (T)_readMapAndClose(createParser(value), valueType);
    }

    /**
     * Method that can be used to serialize any Java value as
     * Ion output, using IonWriter provided.
     *<p>
     * Note: method does not close the underlying writer explicitly
     */
    public void writeValue(IonWriter w, Object value) throws IOException {
        _writeValueAndClose(createGenerator(w), value);
    }

    /**
     * Method that can be used to map any Java value to an IonValue.
     */
    public IonValue writeValueAsIonValue(Object value) throws IOException
    {
        IonFactory f = getFactory();
        IonDatagram container = f._system.newDatagram();

        try (IonWriter writer = f._system.newWriter(container)) {
            writeValue(writer, value);
            IonValue result = container.get(0);
            result.removeFromContainer();
            return result;
        }
    }
}
