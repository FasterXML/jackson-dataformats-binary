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
import com.fasterxml.jackson.databind.module.SimpleModule;

import software.amazon.ion.IonDatagram;
import software.amazon.ion.IonReader;
import software.amazon.ion.IonValue;
import software.amazon.ion.IonWriter;

/**
 * Specialization of {@link ObjectMapper} that will set underlying
 * {@link com.fasterxml.jackson.core.JsonFactory}
 * to be an instance of {@link IonFactory}.
 */
public class IonObjectMapper extends ObjectMapper
{    
    private static final long serialVersionUID = 1L;

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

    public void setCreateBinaryWriters(boolean bin) {
        getFactory().setCreateBinaryWriters(bin);
    }   

    @Override
    public ObjectMapper copy() {
        _checkInvalidCopy(IonObjectMapper.class);
        return new IonObjectMapper(this);
    }

    @Override
    public Version version() {
        return PackageVersion.VERSION;
    }

    @Override
    public IonFactory getFactory() {
        return (IonFactory) _jsonFactory;
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
        return (T)_readMapAndClose(getFactory().createParser(r), _typeFactory.constructType(valueType));
    }

    /**
     * Deserialize an Ion value read from the supplied IonReader into a Java
     * type.
     * <p>
     * Note: method does not close the underlying reader
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> T readValue(IonReader r, TypeReference valueTypeRef) throws IOException {
        return (T)_readMapAndClose(getFactory().createParser(r), _typeFactory.constructType(valueTypeRef));
    }

    /**
     * Deserialize an Ion value read from the supplied IonReader into a Java
     * type.
     * <p>
     * Note: method does not close the underlying reader
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonReader r, JavaType valueType) throws IOException {
        return (T)_readMapAndClose(getFactory().createParser(r), valueType);
    }

    /**
     * Convenience method for converting Ion value into given value type.
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonValue value, Class<T> valueType) throws IOException {
        return (T)_readMapAndClose(getFactory().createParser(value), _typeFactory.constructType(valueType));
    }

    /**
     * Convenience method for converting Ion value into given value type.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public <T> T readValue(IonValue value, TypeReference valueTypeRef) throws IOException {
        return (T)_readMapAndClose(getFactory().createParser(value), _typeFactory.constructType(valueTypeRef));
    }

    /**
     * Convenience method for converting Ion value into given value type.
     */
    @SuppressWarnings("unchecked")
    public <T> T readValue(IonValue value, JavaType valueType) throws IOException  {
        return (T)_readMapAndClose(getFactory().createParser(value), valueType);
    }

    /**
     * Method that can be used to serialize any Java value as
     * Ion output, using IonWriter provided.
     *<p>
     * Note: method does not close the underlying writer explicitly
     */
    public void writeValue(IonWriter w, Object value) throws IOException {
        _configAndWriteValue(getFactory().createGenerator(w), value);
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
