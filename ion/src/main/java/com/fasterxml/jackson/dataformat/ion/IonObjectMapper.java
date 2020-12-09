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
import com.amazon.ion.IonValue;
import com.amazon.ion.IonWriter;

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
        public Builder(IonObjectMapper m) {
            super(m);
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

    public static Builder builder() {
        return new Builder(new IonObjectMapper());
    }

    public static Builder builder(IonFactory streamFactory) {
        return new Builder(new IonObjectMapper(streamFactory));
    }

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
